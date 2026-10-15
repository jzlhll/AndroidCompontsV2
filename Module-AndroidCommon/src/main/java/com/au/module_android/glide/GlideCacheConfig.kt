package com.au.module_android.glide

import android.app.ActivityManager
import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator

/**
 * 空闲缓存与复用池配置，不包含活动 Target 持有的图片；极小图和普通图分别按权重分配。
 * 配置由代码维护：堆预算除数、内存缓存上限、分区权重及磁盘容量须大于 0；
 * Bitmap 池容量与屏数须大于等于 0，磁盘缓存目录不能为空白。
 */
data class GlideCacheConfig(
    val heapBudgetDivisor: Int = 4,
    val memoryCacheMaxBytes: Long = 128L * 1024 * 1024,
    val tinyWeight: Int = 7,
    val normalWeight: Int = 4,
    val bitmapPoolMaxBytes: Long = 16L * 1024 * 1024,
    val bitmapPoolScreens: Float = 1f,
    val diskCacheDirectory: String = "imageCache",
    val diskCacheBytes: Long = 250L * 1024 * 1024,
    val tinyThumbnailPolicy: TinyThumbnailPolicy = TinyThumbnailPolicy.DEFAULT,
)

/** Application 提供进程内固定配置，须在构造时准备，不能依赖 onCreate 中的后续初始化。 */
interface GlideCacheConfigProvider {
    val glideCacheConfig: GlideCacheConfig
}

/** 初始化与图片请求从同一 Application 读取配置，未定制的应用使用默认值。 */
val Context.glideCacheConfig: GlideCacheConfig
    get() = (applicationContext as? GlideCacheConfigProvider)?.glideCacheConfig ?: GlideCacheConfig()

/** 安装图片缓存；网络组件注册和请求默认值仍由 AppGlideModule 负责。 */
fun GlideBuilder.applyGlideCacheConfig(context: Context, config: GlideCacheConfig) {
    // 保持整数分配顺序，避免调节配置引入额外的取整差异。
    val heapBudget = Runtime.getRuntime().maxMemory() / config.heapBudgetDivisor
    val memoryCacheBytes = kotlin.math.min(heapBudget, config.memoryCacheMaxBytes)
    val totalWeight = config.tinyWeight.toLong() + config.normalWeight
    val tinyBytes = memoryCacheBytes * config.tinyWeight / totalWeight
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val calculator = MemorySizeCalculator.Builder(context)
        .setMemoryCacheScreens(0f)
        .setBitmapPoolScreens(if (activityManager.isLowRamDevice) 0f else config.bitmapPoolScreens)
        .build()
    val bitmapPoolBytes = kotlin.math.min(calculator.bitmapPoolSize.toLong(), config.bitmapPoolMaxBytes)
    setMemoryCache(PartitionedMemoryCache(
        tinyBytes = tinyBytes,
        normalBytes = memoryCacheBytes - tinyBytes,
        policy = config.tinyThumbnailPolicy,
    ))
    setBitmapPool(ExactSmallBitmapPool(LruBitmapPool(bitmapPoolBytes), config.tinyThumbnailPolicy))
    setDiskCache(InternalCacheDiskCacheFactory(context, config.diskCacheDirectory, config.diskCacheBytes))
}
