package com.au.module_imagecompressed.compressor.imagesource

import android.graphics.ImageDecoder
import java.io.InputStream

/**
 * 统一图片输入能力，屏蔽 File 与 Uri 的读取差异；实现方不长期持有已打开的流。
 */
interface IImageSource {
    /** 原始文件的字节数，按当前数据源首次查询的结果缓存。 */
    val fileSize: Long

    /** 源文件扩展名，用于选择 RAW 解码路径。 */
    val extension: String

    /** 每次调用均从头打开新的流，由调用方通过 use 关闭；异常交由压缩流程处理。 */
    fun openInputStream(): InputStream?

    /** 创建当前输入对应的系统解码源，不读取先前生成的压缩结果。 */
    fun createImageDecodeSource(): ImageDecoder.Source
}
