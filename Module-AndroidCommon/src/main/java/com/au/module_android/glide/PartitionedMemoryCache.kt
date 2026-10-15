package com.au.module_android.glide

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemoryCache

/** 极小图单独保留，列表图与高清图共用普通缓存；计费、淘汰和回收仍由 Glide 管理。 */
class PartitionedMemoryCache(
    tinyBytes: Long,
    normalBytes: Long,
    private val policy: TinyThumbnailPolicy = TinyThumbnailPolicy.DEFAULT,
) : MemoryCache {
    private val tiny = LruResourceCache(tinyBytes)
    private val normal = LruResourceCache(normalBytes)
    private val partitions = listOf(tiny, normal)
    private var listener: MemoryCache.ResourceRemovedListener? = null

    /** 所有分区的实际资源字节数。 */
    @Synchronized
    override fun getCurrentSize(): Long = partitions.sumOf { it.currentSize }

    /** 所有分区的当前配额总和，不包含在用资源与复用池。 */
    @Synchronized
    override fun getMaxSize(): Long = partitions.sumOf { it.maxSize }

    /** 一致缩放各档配额，不允许跨档借用容量。 */
    @Synchronized
    override fun setSizeMultiplier(multiplier: Float) {
        partitions.forEach { it.setSizeMultiplier(multiplier) }
    }

    /** 将命中资源交还 Engine，此处不能触发回收回调。 */
    @Synchronized
    override fun remove(key: Key): Resource<*>? {
        partitions.forEach { partition ->
            partition.remove(key)?.let {
                return it
            }
        }
        return null
    }

    /** 依据 Bitmap 实际尺寸与 Resource 大小入库，每个 Key 只存一份。 */
    @Synchronized
    override fun put(key: Key, resource: Resource<*>?): Resource<*>? {
        val value = resource?.get()
        val bitmap = when (value) {
            is Bitmap -> value
            is BitmapDrawable -> value.bitmap
            else -> null
        }
        val isTiny = bitmap != null && bitmap.width <= policy.maxDimension && bitmap.height <= policy.maxDimension
        // 小尺寸但分配过大的资源不得转存其他档，避免掩盖池复用导致的膨胀。
        if (resource != null && bitmap != null && isTiny &&
            (bitmap.allocationByteCount > policy.maxAllocationBytes || resource.size > policy.maxAllocationBytes)
        ) {
            listener?.onResourceRemoved(resource)
            return null
        }
        val target = if (isTiny) tiny else normal
        // 与原生 LRU 相同：超限新资源拒收时，保留同 Key 的旧资源。
        if (resource != null && resource.size >= target.maxSize) {
            listener?.onResourceRemoved(resource)
            return null
        }
        var previous: Resource<*>? = null
        partitions.forEach { partition ->
            if (partition !== target) {
                partition.remove(key)?.let { old ->
                    previous = old
                    if (old != resource) {
                        listener?.onResourceRemoved(old)
                    }
                }
            }
        }
        val replaced = target.put(key, resource)
        return replaced ?: previous
    }

    /** 将原生 LRU 淘汰通知统一交给 Glide Engine。 */
    @Synchronized
    override fun setResourceRemovedListener(listener: MemoryCache.ResourceRemovedListener) {
        this.listener = listener
        partitions.forEach { partition ->
            partition.setResourceRemovedListener(listener)
        }
    }

    /** 释放全部空闲资源，遵循 Glide 的资源回收生命周期。 */
    @Synchronized
    override fun clearMemory() {
        partitions.forEach { it.clearMemory() }
    }

    /** 系统内存压力完整传递到每个分区。 */
    @Synchronized
    override fun trimMemory(level: Int) {
        partitions.forEach { it.trimMemory(level) }
    }
}
