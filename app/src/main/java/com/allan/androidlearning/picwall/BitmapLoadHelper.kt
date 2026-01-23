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
import com.au.module_android.utilsmedia.ThumbnailCompatUtil
import com.au.module_imagecompressed.PickUriWrap
import com.au.module_imagecompressed.compressor.ImageLoader
import com.au.module_imagecompressed.compressor.loadImage
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
                LOW -> ThumbnailCompatUtil.LOW_SIZE
                MID -> ThumbnailCompatUtil.MID_SIZE
                HIGH -> null
            }
        }
    }

    companion object {
        private const val BITMAP_CACHE_MAX_SIZE = 120 * 1024 * 1024 // 50MB
        private const val THREAD_POOL_SIZE = 4
    }

    private val thumbnailUtils = ThumbnailCompatUtil(Globals.app)

    private val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(BITMAP_CACHE_MAX_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount // 返回真实字节大小
        }

        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            logdNoFile { "cache entry removed: $key, evicted=$evicted, size=${oldValue?.byteCount}" }
        }
    }

    private var loadExecutor: ExecutorService? = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadingKeys = Collections.synchronizedSet(HashSet<String>())

    private var mIsSliding = false
    private var mIsScaling = false
    private var lastScale = 1.0f

    private var allImageBeans: List<PickUriWrap> = emptyList()
    private val blockIndexMap = HashMap<String, Int>()

    /**
     * 初始化数据源
     */
    fun onInitial(frameImageList: List<PickUriWrap>) {
        this.allImageBeans = frameImageList
        blockIndexMap.clear()
        bitmapCache.evictAll()
        logdNoFile { "onInitial: cache cleared" }
    }

    /**
     * 处理滑动状态变化
     */
    fun onScrollStateChanged(isSliding: Boolean) {
        this.mIsSliding = isSliding
        if (!mIsSliding && !mIsScaling) {
            logMemoryUsage("Scroll End")
        }
    }

    /**
     * 缩放开始
     */
    fun onScaleStart(scaleCenter: PointF, scale: Float) {
        this.mIsScaling = true
        // 缩放开始：不暂停加载任务，允许缩放过程中加载
        if (loadExecutor == null || loadExecutor!!.isShutdown) {
            loadExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        }
    }

    /**
     * 缩放结束
     */
    fun onScaleEnd(scaleCenter: PointF, scale: Float) {
        this.mIsScaling = false
        // 重启加载任务
        if (loadExecutor == null || loadExecutor!!.isShutdown) {
            loadExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        }
        if(BuildConfig.DEBUG) logMemoryUsage("Scale End")
    }

    private fun logMemoryUsage(reason: String) {
        val totalSize = bitmapCache.size()
        val sizeMB = totalSize / 1024f / 1024f
        logdNoFile { "Memory Usage ($reason): ${"%.2f".format(sizeMB)} MB ($totalSize bytes)" }
    }

    /**
     * 清理与当前缩放倍数不匹配的缓存
     */
    fun cleanScaleMismatchCache(scale: Float) {
        // 移除激进的清空逻辑，交由 LRU 自动管理，避免闪烁
        lastScale = scale
    }

    /**
     * 为Block分配固定的ImageBean
     */
    fun assignImageForBlock(key: String): PickUriWrap? {
        if (allImageBeans.isEmpty()) {
            return null
        }
        val index = blockIndexMap.getOrPut(key) {
            allImageBeans.indices.random()
        }
        return allImageBeans.getOrNull(index)
    }

    /**
     * 获取Bitmap
     * 如果缓存中有，直接返回；
     * 如果没有且当前允许加载，则触发加载并返回null（占位）；
     */
    fun getBitmap(blockInfo: BlockInfo, scale: Float, imageBean: PickUriWrap?) : Bitmap? {
        if (allImageBeans.isEmpty() || imageBean == null) {
            return null
        }
        val currentQuality = if(blockInfo.isRealVisible) Quality.fromScale(scale) else Quality.LOW
        val key = generateKey(blockInfo.centerPoint, currentQuality)
        var bitmap = bitmapCache.get(key)

        var needReload = false
        if (bitmap == null) {
            needReload = true
            // 降级查找：如果当前需要更高级（HIGH），尝试使用 LOW 占位
            if (currentQuality.level > Quality.LOW.level) {
                val fallbackKey = generateKey(blockInfo.centerPoint, Quality.LOW)
                val fallbackBitmap = bitmapCache.get(fallbackKey)
                if (fallbackBitmap != null) {
                    bitmap = fallbackBitmap
                }
            }
        }

        if (needReload) {
            loadBitmapAsync(key, blockInfo, scale, imageBean)
        }
        
        // 即使需要重新加载，也返回旧 Bitmap 以避免闪烁
        return bitmap
    }

    private fun loadBitmapAsync(key: String, blockInfo: BlockInfo, scale: Float, imageBean: PickUriWrap) {
        val executor = loadExecutor
        if (executor == null || executor.isShutdown) return

        if (loadingKeys.contains(key)) {
            logdNoFile { "load task (skip already loading): $key" }
            return
        }

        loadingKeys.add(key)
        logdNoFile { "submit load task: $key" }

        executor.execute {
            val bitmap = loadBitmapInThread(blockInfo, scale, imageBean)

            mainHandler.post {
                loadingKeys.remove(key)
                if (bitmap != null) {
                    bitmapCache.put(key, bitmap)
                    logdNoFile { "bitmap loaded: $key" }
                    listener?.onBitmapLoaded(blockInfo, scale)
                }
            }
        }
    }

    private val myCompressConfig = ImageLoader.Config(
        maxWidth = 1440,
        maxHeight = 1920,
        ignoreSizeInKB = 1024 * 500,
    )

    private fun loadBitmapInThread(blockInfo: BlockInfo, scale: Float, imageBean: PickUriWrap) : Bitmap? {
        // 加载 Bitmap 逻辑
        val quality = if(blockInfo.isRealVisible) Quality.fromScale(scale) else Quality.LOW
        val size = quality.scaleToSize()
        if (size == null) {
            //加载原图
            //使用我的策略进行略微压缩加载避免过大
            val bitmap = runBlocking {
                loadImage(Globals.app, imageBean.uriParsedInfo.uri, myCompressConfig)
            }
            return bitmap
        }
        return thumbnailUtils.loadThumbnailCompat(imageBean.uriParsedInfo.uri, size)
    }

    private fun generateKey(center: PointF, quality: Quality): String {
        // Key不再包含 Scale，以便在缩放时复用旧 Bitmap
        return "${center.x.toInt()}_${center.y.toInt()}_${quality.name}"
    }

    fun onDestroy() {
        loadExecutor?.shutdownNow()
        bitmapCache.evictAll()
        logdNoFile { "onDestroy: cache cleared" }
    }

    /**
     * 恢复加载（如 View 重新 Attach）
     */
    fun onAttachedToWindow() {
        if (loadExecutor == null || loadExecutor!!.isShutdown) {
            loadExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
            loadingKeys.clear()
        }
    }
}
