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
        private const val BITMAP_CACHE_MAX_SIZE = 50 * 1024 * 1024
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

    private val executor = LIFOThreadPoolUtil.createFixedLIFOThreadPool(4)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mIsScrolling = false
    private var mIsScaling = false
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
            val key = generateKey(blockInfo.centerPoint, quality)
            val bitmap = bitmapCache.get(key)
            if (bitmap != null) {
                return bitmap
            }
        }
        return null
    }

    fun loadAllBitmapsAsync(blockInfos: List<BlockInfo>, scale: Float) {
        logdNoFile { "load all bitmap..." }
        val runList = ArrayList<Runnable>()
        blockInfos.forEach { blockInfo->
            runList.add {
                val imageBean = assignImageForBlock(blockInfo.key)
                if (imageBean != null) {
                    val currentQuality = if(blockInfo.isRealVisible) Quality.fromScale(scale) else Quality.LOW
                    val key = generateKey(blockInfo.centerPoint, currentQuality)
                    val newGenerateBitmap = loadBitmapInThread(blockInfo, scale, imageBean)
                    bitmapCache.put(key, newGenerateBitmap)
                    mainHandler.post {
                        if (newGenerateBitmap != null) {
                            logdNoFile { "bitmap loaded: $key" }
                            listener?.onBitmapLoaded(blockInfo, scale)
                        }
                    }
                }
            }
        }

        for (run in runList) {
            executor.execute(run)
        }
    }

    private val myCompressConfig = ImageLoader.Config(
        maxWidth = 1440,
        maxHeight = 1920,
        ignoreSizeInKB = 1024 * 1024 * 2,
    )

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

    fun onDetachedFromWindow() {
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
