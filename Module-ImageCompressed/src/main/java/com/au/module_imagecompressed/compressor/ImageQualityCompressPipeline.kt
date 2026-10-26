package com.au.module_imagecompressed.compressor

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.au.module_android.log.logdNoFile
import com.au.module_imagecompressed.compressor.imagesource.IImageSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 质量流水线：按配置使用固定质量，单次编码后写回拍摄信息及输出尺寸、方向。
 */
internal class ImageQualityCompressPipeline(
    private val source: IImageSource,
    private val config: BestImageCompressor.Config,
) {
    companion object {
        // 对齐 iOS 的 GPS、EXIF、TIFF 中可读写字段，像素、色彩和容器布局仍由输出图决定。
        private val EXIF_TAGS_TO_PRESERVE = listOf(
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_X_RESOLUTION,
            ExifInterface.TAG_Y_RESOLUTION,
            ExifInterface.TAG_RESOLUTION_UNIT,
            ExifInterface.TAG_DATETIME,

            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SPECIFICATION,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_EXIF_VERSION,
            ExifInterface.TAG_FLASHPIX_VERSION,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_RELATED_SOUND_FILE,

            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_SPECTRAL_SENSITIVITY,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_SENSITIVITY_TYPE,
            ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY,
            ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX,
            ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY,
            ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_BRIGHTNESS_VALUE,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_MAX_APERTURE_VALUE,
            ExifInterface.TAG_SUBJECT_DISTANCE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH_ENERGY,
            ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
            ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
            ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
            ExifInterface.TAG_EXPOSURE_INDEX,
            ExifInterface.TAG_SENSING_METHOD,
            ExifInterface.TAG_CUSTOM_RENDERED,
            ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_GAIN_CONTROL,
            ExifInterface.TAG_CONTRAST,
            ExifInterface.TAG_SATURATION,
            ExifInterface.TAG_SHARPNESS,
            ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,

            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,

            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        )
    }

    /** 编码并写入 EXIF，输入 Bitmap 的回收由调用方负责。 */
    fun process(
        bitmap: Bitmap,
        outputFile: File,
        sourceExif: ExifInterface,
        orientation: Int,
        originalWidth: Int,
        originalHeight: Int,
    ): File {
        FileOutputStream(outputFile).use { out ->
            check(bitmap.compress(config.outputFormat, config.quality, out)) {
                "Image compression failed: ${config.outputFormat}"
            }
        }
        // 编码质量选定后只落盘一次，再补回需要保留的 EXIF。
        val outputExif = ExifInterface(outputFile)
        for (tag in EXIF_TAGS_TO_PRESERVE) {
            sourceExif.getAttribute(tag)?.let { outputExif.setAttribute(tag, it) }
        }
        val xmpAddedTags = sourceExif.copyMissingXmpCaptureMetadataTo(outputExif)
        // 尺寸始终描述输出像素，不复制原图尺寸或缩略图偏移。
        outputExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, bitmap.width.toString())
        outputExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, bitmap.height.toString())
        outputExif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, bitmap.width.toString())
        outputExif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, bitmap.height.toString())
        // RAW 未旋转的像素保留源方向；已校正方向的解码结果写 NORMAL。
        outputExif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        try {
            outputExif.saveAttributes()
        } catch (e: IOException) {
            if (xmpAddedTags.isEmpty()) throw e
            // 可选 XMP 写回失败时恢复基础 EXIF，不把补全失败作为压缩失败条件。
            logdNoFile("ImageProcess") { "save XMP capture metadata skipped: ${e.message}" }
            for (tag in xmpAddedTags) outputExif.setAttribute(tag, null)
            outputExif.saveAttributes()
        }
        logdNoFile("ImageProcess") {
            "quality pipeline: source ${source.extension} ${originalWidth}x$originalHeight ${source.fileSize} bytes, " +
                "size ${bitmap.width}x${bitmap.height}, format ${config.outputFormat}, " +
                "quality ${config.quality}, " +
                "file size ${outputFile.length()} bytes"
        }
        return outputFile
    }
}
