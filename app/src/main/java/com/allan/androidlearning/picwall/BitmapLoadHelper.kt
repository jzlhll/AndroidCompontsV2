package com.allan.androidlearning.picwall

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import com.allan.androidlearning.BuildConfig
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_imagecompressed.PickUriWrap
import com.au.module_imagecompressed.compressor.ImageHybridLoaderUtil
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import kotlin.collections.iterator

/**
 * 图片加载辅助类
 * 管控本地图片加载/停止加载逻辑，维护Bitmap缓存
 */
class BitmapLoadHelper(private val listener: OnBitmapLoadListener?) {

    interface OnBitmapLoadListener {
        fun onBitmapLoaded(blockInfo: BlockInfo, scale: Float)
    }

    enum class Quality(val level: Int) {
        LOW(1),
        MID(2),
        HIGH(3);

        companion object {
            fun fromScale(scale: Float): Quality {
                if (scale >= 0.65f) {
                    return HIGH
                }
                if (scale >= 0.4f) {
                    return MID
                }

                return LOW
            }
        }

        fun scaleToSize() : Size? {
            return when (this) {
                LOW -> ImageHybridLoaderUtil.LOW_SIZE
                MID -> ImageHybridLoaderUtil.MID_SIZE
                HIGH -> null
            }
        }
    }

    companion object {
        private const val BITMAP_CACHE_MAX_SIZE = 128 * 1024 * 1024
    }

    private val thumbnailUtils = ImageHybridLoaderUtil(Globals.app)

    private val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(BITMAP_CACHE_MAX_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount // 返回真实字节大小
        }

        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            logdNoFile { "cache entry removed: $key, evicted=$evicted, size=${oldValue?.allocationByteCount}" }
        }
    }

    private val executor = LIFOThreadPoolUtil.createFixedLIFOThreadPool(4)
    // 任务管理 Map: Key=Block.key, Value=Future
    private val runningTasks = ConcurrentHashMap<Long, Future<*>>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mIsScrolling = false
    private var mIsScaling = false
    private var allImageBeans: List<PickUriWrap> = emptyList()
    private val blockIndexMap = HashMap<Long, Int>()

    /**
     * 初始化数据源
     */
    fun onInitial(frameImageList: List<PickUriWrap>) {
        this.allImageBeans = frameImageList
        blockIndexMap.clear()
        bitmapCache.evictAll()
        cancelAllTasks()
        logdNoFile { "onInitial: cache cleared" }
    }

    private fun cancelAllTasks() {
        runningTasks.values.forEach { it.cancel(false) }
        runningTasks.clear()
    }

    /**
     * 处理滑动状态变化
     */
    fun onScrollStateChanged(isScrolling: Boolean) {
        this.mIsScrolling = isScrolling
        if (!mIsScrolling && !mIsScaling) {
            logMemoryUsage("Scroll End")
        }
    }

    /**
     * 缩放开始
     */
    fun onScaleStart(scaleCenter: PointF, scale: Float) {
        this.mIsScaling = true
    }

    /**
     * 缩放结束
     */
    fun onScaleEnd(scaleCenter: PointF, scale: Float) {
        this.mIsScaling = false
        if(BuildConfig.DEBUG) logMemoryUsage("Scale End")
    }

    private fun logMemoryUsage(reason: String) {
        logdNoFile {
            val totalSize = bitmapCache.size()
            val sizeMB = totalSize / 1024f / 1024f
            "Memory Usage ($reason): ${"%.2f".format(sizeMB)} MB ($totalSize bytes)"
        }
    }

    /**
     * 为Block分配固定的ImageBean
     */
    fun assignImageForBlock(key: Long): PickUriWrap? {
        if (allImageBeans.isEmpty()) {
            return null
        }
        val index = blockIndexMap.getOrPut(key) {
            allImageBeans.indices.random()
        }
        return allImageBeans.getOrNull(index)
    }

    /**
     * 获取缓存的 Bitmap
     */
    fun getCachedBitmap(blockInfo: BlockInfo, scale: Float, useLowQuality: Boolean) : Bitmap? {
        //无需判断
//        if (allImageBeans.isEmpty()) {
//            return null
//        }
        // 确定当前质量级别
        val currentQuality = if(blockInfo.isRealVisible) Quality.fromScale(scale) else Quality.LOW
        
        // 构建质量查找序列：当前质量 -> 降级质量（如果允许）
        val qualityLookupList = if (useLowQuality) {
            // 根据当前质量级别，生成从高到低的降级质量序列
            when (currentQuality) {
                Quality.HIGH -> listOf(Quality.HIGH, Quality.MID, Quality.LOW)
                Quality.MID -> listOf(Quality.MID, Quality.LOW)
                else -> listOf(Quality.LOW)
            }
        } else {
            listOf(currentQuality)
        }

        // 遍历质量序列，查找第一个可用的位图
        for (quality in qualityLookupList) {
            val key = generateCacheKey(blockInfo.key, quality)
            val bitmap = bitmapCache.get(key)
            if (bitmap != null) {
                return bitmap
            }
        }
        return null
    }

    fun loadAllBitmapsAsync(blockInfos: List<BlockInfo>, scale: Float) {
        logdNoFile { "load all bitmap..." }
        
        // 1. 找出当前可见区域的所有 Block Key
        val visibleKeys = blockInfos.map { it.key }.toSet()

        // 2. 取消那些已经滑出屏幕（不再 visibleKeys 中）的任务
        val iterator = runningTasks.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!visibleKeys.contains(entry.key)) {
                entry.value.cancel(false) // 尝试取消
                iterator.remove()
                logdNoFile { "Task cancelled for block: ${entry.key}" }
            }
        }

        // 3. 提交新任务
        blockInfos.forEach { blockInfo ->
            // 如果已经在运行或者已有缓存，则跳过
            // 这里简单判断：只要有任务在跑就不重复提交。LIFO 线程池会优先处理后提交的。
            // 注意：如果 Scale 变了导致 Quality 变了，可能需要重新加载。
            // 但目前的逻辑主要针对滑动。为了简化，假设同一个 Block 在队列中只需存在一份。
            if (runningTasks.containsKey(blockInfo.key)) {
                return@forEach
            }

            // 检查缓存，如果有就不提交任务
            if (getCachedBitmap(blockInfo, scale, false) != null) {
                return@forEach
            }

            val task = executor.submit {
                try {
                    // 任务开始前再次检查是否被取消（虽然 Future.cancel 会置位，但双重保险）
                    if (Thread.currentThread().isInterrupted) return@submit

                    val imageBean = assignImageForBlock(blockInfo.key)
                    if (imageBean != null) {
                        val currentQuality = if(blockInfo.isRealVisible) Quality.fromScale(scale) else Quality.LOW
                        val cacheKey = generateCacheKey(blockInfo.key, currentQuality)
                        
                        val newGenerateBitmap = loadBitmapInThread(blockInfo, scale, imageBean)
                        
                        if (Thread.currentThread().isInterrupted) return@submit

                        if (newGenerateBitmap != null) {
                            bitmapCache.put(cacheKey, newGenerateBitmap)
                            mainHandler.post {
                                logdNoFile { "bitmap loaded: $cacheKey ${newGenerateBitmap.allocationByteCount}" }
                                listener?.onBitmapLoaded(blockInfo, scale)
                                // 任务完成后移除
                                runningTasks.remove(blockInfo.key)
                            }
                        } else {
                            runningTasks.remove(blockInfo.key)
                        }
                    } else {
                        runningTasks.remove(blockInfo.key)
                    }
                } catch (e: Exception) {
                    runningTasks.remove(blockInfo.key)
                }
            }
            runningTasks[blockInfo.key] = task
        }
    }

    private fun loadBitmapInThread(blockInfo: BlockInfo, scale: Float, imageBean: PickUriWrap) : Bitmap? {
        val cacheBitmap = getCachedBitmap(blockInfo, scale, false)
        if (cacheBitmap != null) {
            return cacheBitmap
        }

        // 加载 Bitmap 逻辑
        val quality = if(blockInfo.isRealVisible) Quality.fromScale(scale) else Quality.LOW
        val size = quality.scaleToSize()
        if (size == null) {
            //加载原图
            //使用我的策略进行略微压缩加载避免过大
            val bitmap = runBlocking {
                thumbnailUtils.loadUri(imageBean.uriParsedInfo.uri, null)
            }
            return bitmap
        }
        return runBlocking {
            thumbnailUtils.loadUri(imageBean.uriParsedInfo.uri, size)
        }
    }

    private fun generateCacheKey(blockKey: Long, quality: Quality): String {
        // Cache Key 依然保留字符串形式，因为 LruCache 是 String Key
        // 但基础部分改用 Long Key
        return "${blockKey}_${quality.name}"
    }

    fun onDetachedFromWindow() {
        cancelAllTasks()
        executor.shutdownNow()
        bitmapCache.evictAll()
        logdNoFile { "onDestroy: cache cleared" }
    }

    /**
     * 恢复加载（如 View 重新 Attach）
     */
    fun onAttachedToWindow() {
    }
}
