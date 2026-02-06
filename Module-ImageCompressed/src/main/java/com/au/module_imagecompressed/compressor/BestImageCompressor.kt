package com.au.module_imagecompressed.compressor

import android.graphics.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.au.module_android.utils.ignoreError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * 图片压缩类1.1版
 *
 *  文件压缩： systemCompressFile
 *  Uri压缩： systemCompressUri
 *  失败回退拷贝： systemCompressUriOrCopy
 *  关键配置 Config :
 *  maxWidth / maxHeight：最大边长（默认 1440x1920），控制解码与缩放目标尺寸 Config
 *  qualityType：压缩质量策略 default/deep/shallow，按原图大小自动选质量 chooseQuality
 *  supportScale / supportScaleRatio：是否缩放及“接近阈值”（默认 1.2），仅在尺寸接近目标时缩放 compressOnce 判定
 *  secondReduce：二次压缩达标体积（含 targetSize、qualityStep、secondScaleRatio、tryReduceCount） SecondReduce
 *  outputFormat：期望输出格式（默认 JPEG）；当前写出固定 JPEG，可按需扩展 compressOnce 写出
 *
 *  行为简述
 *  自动解码与采样、按需缩放与Exif旋转，写入压缩文件 compressOnce
 *  若设定 targetSize，按步长降质量并小幅二次缩放，限次尝试达标 compress
 */
class BestImageCompressor(
    val config : Config,
    val provideInputStream: () -> InputStream?,
    val provideFileSize: () -> Long,
    val provideImageDecodeSource:() ->ImageDecoder.Source?,
) {
    companion object {
        private const val TAG = "au-Compressor"
        const val DEFAULT_IGNORE_KB = 150
    }

    data class Config(
        /** 最大边长限制，防止内存溢出，也能显著降低图片大小 */
        val maxWidth: Int = 1440,
        /** 最大边长限制，防止内存溢出，也能显著降低图片大小 */
        val maxHeight: Int = 1920,

        /**  压缩质量选择，默认；可选，deep，压缩的更狠；shallow，比默认轻微压缩。*/
        val qualityType:String = "default",

        /** 压缩的目标大小，如果目标为null，则只进行首次压缩，不会进行二次reduce操作 */
        val secondReduce: SecondReduce? = null,

        /** 是否支持缩放, 推荐打开。尽量往1920靠，如果低于则不做scale */
        val supportScale: Boolean = true,
        /** 采样后的size和代码中期待的size，出现偏差的范围 1/supportScaleRatio ~ supportScaleRatio都不做scale。 比如大于1f，否则出错。*/
        val supportScaleRatio: Float = 1.2f,
        /**
         * 忽略列表，不做压缩
         */
        val ignoreFileTypes:List<String> = listOf("gif", "webp", "svg"),
        /**
         * 多少kb就忽略
         */
        val ignoreSizeInKB : Int = DEFAULT_IGNORE_KB,
        /**
         * 如果压缩失败，或者ignore后，选择的做法：
         *
         * always 不论是啥都进行拷贝
         *
         * onlyUri 只把远程uri进行拷贝到本地
         *
         * none/null 如果压缩失败，或者ignore都不进行拷贝
         */
        val alwaysCopyMode : String? = "onlyUri"
    )

    data class SecondReduce(
        /**
         * 其实这个参数很难去衡量，按照现在2025-2026年的手机水平，照片大约在3M-6M之间。设计了默认的参数。
         * 如果首次压缩后，超标，为了达到targetSize，将进行reduce操作：循环每次下降qualityStep质量，允许的次数
         */
        val targetSize: Long,
        /** 每次quality下降的步长，默认5 */
        val qualityStep: Int = 5,
        /** reduce操作的时候，会基于原来的scaleRatio进行二次缩放，进一步减少图片尺寸*/
        val secondScaleRatio: Float = 0.9f,
        /** 尝试次数，如果尝试次数达到，则放弃尝试 */
        val tryReduceCount: Int = 3,
    )

    private val tempBufferSize = 16 * 1024

    private val api: IApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        NewApi()
    } else {
        OldApi()
    }

    private data class Decoded(
        var originalW:Int, var originalH:Int,
        var targetW:Int, var targetH:Int, var sampleSize:Int, var bitmap: Bitmap?) {
        override fun toString(): String {
            return "orig:${originalW}x$originalH, sample:$sampleSize, wish size:${targetW}x$targetH, decoded bitmap:${bitmap?.width}x${bitmap?.height}"
        }
    }

    private interface IApi {
        fun decodeBitmap() : Decoded
    }

    var mFileSize:Long = -1L

    suspend fun compress() : File?{
        return withContext(Dispatchers.IO) {
            ignoreError {
                val outputFile = CompressCacheConstManager.createCompressOutputFile()
                val decoded = api.decodeBitmap()
                Log.d(TAG, "compress: $decoded")
                val bitmap = decoded.bitmap ?: return@withContext null
                val targetW = decoded.targetW
                val targetH = decoded.targetH

                // 查询文件大小 放到外面避免多次查询
                mFileSize = provideFileSize()
                val quality = CompressUtil.chooseQuality(mFileSize, config.qualityType)
                Log.d(TAG, "compress: quality $quality type: ${config.qualityType}")

                val targetSize = config.secondReduce?.targetSize

                val onceBitmap = compressOnce(bitmap, outputFile, targetW, targetH,
                        quality, 0, targetSize, 1)
                if (onceBitmap != bitmap) {
                    bitmap.recycle()
                }

                if (targetSize != null && outputFile.length() > targetSize) {
                    val qualityStep = config.secondReduce.qualityStep
                    val tryReduceCount = config.secondReduce.tryReduceCount
                    val reduceBitmap = compressOnce(onceBitmap, outputFile,
                        (targetW * config.secondReduce.secondScaleRatio).roundToInt(),
                        (targetH  * config.secondReduce.secondScaleRatio).roundToInt(),
                        quality,
                        qualityStep,
                        targetSize,
                        tryReduceCount)
                    onceBitmap.recycle()
                    reduceBitmap.recycle()
                } else {
                    onceBitmap.recycle()
                }
                outputFile
            }
        }
    }

    //返回值是给下一轮使用
    private suspend fun compressOnce(inputBitmap: Bitmap,
                             outputFile: File,
                             targetW:Int, targetH:Int,
                             quality:Int,
                             qualityStep:Int,
                             targetSize:Long?,
                             qualityRunCount: Int) : Bitmap {
        var needScale = config.supportScale
            && targetW > 0 && targetH > 0
            && (targetW < inputBitmap.width || targetH < inputBitmap.height)
        if (config.supportScaleRatio > 1f) {
            val ratio = config.supportScaleRatio
            val ratioDown = 1f / ratio
            //判断inputBitmap是否在ratioUp和ratioDown与targetW/H区间内
            needScale = needScale && inputBitmap.width in (targetW * ratioDown).roundToInt()..(targetW * ratio).roundToInt()
                    && inputBitmap.height in (targetH * ratioDown).roundToInt()..(targetH * ratio).roundToInt()
        }
        Log.d(TAG, "compressOnce: size $targetW $targetH, quality $quality step$qualityStep, target:$targetSize try:$qualityRunCount needScale:$needScale")

        val bitmap2 = if (needScale) {
            inputBitmap.scaleTo(targetW, targetH).also {
                if (it != inputBitmap) {
                    inputBitmap.recycle()
                }
            }
        } else {
            inputBitmap
        }

        val bitmap = bitmap2.rotateByExif { provideInputStream() }
        if (bitmap2 != bitmap) {
            bitmap2.recycle()
        }

        // 6. 写入输出文件
        var count = qualityRunCount //默认是1，保证不二次压缩的正常运行
        var curQuality = quality
        while (count-- > 0) {
            if (outputFile.exists()) {
                outputFile.delete()
                delay(100)
            }
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, curQuality, out)
            }

            if (targetSize != null && outputFile.length() <= targetSize) {
                break
            }

            curQuality -= qualityStep
        }
        //经历过一次scale和一次rotate后的bitmap
        Log.d(TAG, "compressOnce: after: size:${bitmap.width} ${bitmap.height}")
        return bitmap
    }

    /**
     * Bitmap 缩放扩展函数
     */
    private fun Bitmap.scaleTo(wishWidth: Int, wishHeight: Int) : Bitmap {
        // 修复点1：将硬件位图转换为软件位图
        val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.getConfig() == Bitmap.Config.HARDWARE) {
            // 硬件位图转换为软件位图（选择ARGB_8888保证兼容性，也可根据需求用RGB_565）
            this.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            // 非硬件位图直接使用
            this
        }

        val scaled = createBitmap(wishWidth, wishHeight, Bitmap.Config.RGB_565)
        val ratioX = wishWidth / this.width.toFloat()
        val ratioY = wishHeight / this.height.toFloat()
        val midX = wishWidth / 2f
        val midY = wishHeight / 2f

        val matrix = Matrix().apply { setScale(ratioX, ratioY, midX, midY) }
        Canvas(scaled).apply {
            setMatrix(matrix)
            drawBitmap(
                softwareBitmap,
                midX - this@scaleTo.width / 2f,
                midY - this@scaleTo.height / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
        // 修复点3：如果创建了新的软件位图，回收临时对象（避免内存泄漏）
        if (softwareBitmap !== this) {
            softwareBitmap.recycle()
        }

        Log.d(TAG, "Scaled to $wishWidth * $wishHeight (${scaled.width} * ${scaled.height})")
        return scaled
    }

    /**
     * 根据Exif信息旋转Bitmap（适配File输入）
     */
    private fun Bitmap.rotateByExif(inputStreamProvider: () -> InputStream?): Bitmap {
        try {
            inputStreamProvider()?.use {
                val exif = ExifInterface(it)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )

                val rotateMatrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                        else -> {}
                    }
                }
                Log.d(TAG, "rotate Exif: $orientation")
                return Bitmap.createBitmap(this, 0, 0, width, height, rotateMatrix, true)
            }
        } catch (_: Exception) {
            Log.d(TAG, "rotate Exif: error")
        }
        return this
    }

    //region decoded 接口实现
    private inner class OldApi : IApi {
        override fun decodeBitmap() : Decoded {
            // 1. 解码图片边界（仅获取宽高，不加载完整Bitmap）
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            provideInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // 2. 计算目标缩放尺寸
            val (targetWidth, targetHeight) = CompressUtil.calculateTargetDimensions(
                options.outWidth,
                options.outHeight,
                config.maxWidth.toFloat(),
                config.maxHeight.toFloat()
            )

            // 3. 计算采样率
            val sampleSize = CompressUtil.calculateSampleSize(
                options.outWidth,
                options.outHeight,
                targetWidth,
                targetHeight
            )

            // 4. 解码并处理Bitmap（缩放+旋转）
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
                inTempStorage = ByteArray(tempBufferSize)
            }
            val bitmap = provideInputStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }

            return Decoded(options.outWidth, options.outHeight, targetWidth, targetHeight, sampleSize, bitmap)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private inner class NewApi : IApi {
        override fun decodeBitmap(): Decoded {
            val decoded = Decoded(0, 0, 0, 0, 0, null)
            //1. 解码图片
            decoded.bitmap = ImageDecoder.decodeBitmap(provideImageDecodeSource()!!) { decoder, info, _ ->
                // 通过ImageInfo获取原始尺寸
                val originalWidth = info.size.width
                val originalHeight = info.size.height
                decoded.originalW = originalWidth
                decoded.originalH = originalHeight

                // 2. 计算目标缩放尺寸
                val (targetWidth, targetHeight) = CompressUtil.calculateTargetDimensions(
                    originalWidth,
                    originalHeight,
                    config.maxWidth.toFloat(),
                    config.maxHeight.toFloat()
                )
                decoded.targetW = targetWidth
                decoded.targetH = targetHeight

                // 3. 计算采样率
                val sampleSize = CompressUtil.calculateSampleSize(
                    originalWidth,
                    originalHeight,
                    targetWidth,
                    targetHeight
                )
                decoded.sampleSize = sampleSize
                // 计算采样尺寸
                decoder.setTargetSampleSize(sampleSize)
            }
            return decoded
        }
    }

    //endregion
}