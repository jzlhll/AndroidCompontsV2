package com.au.jobstudy.words

import android.content.Context
import java.io.File

/**
 * Excel文件解析器接口
 * 用于解析Excel文件并提取单词数据
 */
interface ExcelParser {
    
    /**
     * 初始化解析器
     * @param context Android上下文，用于访问资源
     */
    fun initialize(context: Context)
    
    /**
     * 获取Excel文件中的所有工作表名称
     * @param file Excel文件
     * @return 工作表名称列表
     * @throws ExcelParseException 当解析失败时抛出异常
     */
    @Throws(ExcelParseException::class)
    fun getAllSheetNames(file: File): List<String>
    
    /**
     * 获取指定工作表中的所有行数据
     * @param file Excel文件
     * @param sheetName 工作表名称
     * @return 表格行数据列表，每行是一个字符串列表
     * @throws ExcelParseException 当解析失败时抛出异常
     */
    @Throws(ExcelParseException::class)
    fun getAllRowsFromSheet(file: File, sheetName: String): List<List<String>>
    
    /**
     * 获取指定工作表中的所有WordBean数据
     * @param file Excel文件
     * @param sheetName 工作表名称
     * @return WordBean列表
     * @throws ExcelParseException 当解析失败时抛出异常
     */
    @Throws(ExcelParseException::class)
    fun getAllWordsFromSheet(file: File, sheetName: String): List<WordBean>
    
    /**
     * 将单行数据转换为WordBean
     * @param rowData 表格行数据
     * @return WordBean对象
     * @throws ExcelParseException 当数据格式不正确时抛出异常
     */
    @Throws(ExcelParseException::class)
    fun convertRowToWordBean(rowData: List<String>): WordBean
    
    /**
     * 验证Excel文件格式是否正确
     * @param file Excel文件
     * @return 是否为有效的Excel文件
     */
    fun isValidExcelFile(file: File): Boolean
    
    /**
     * 获取Excel文件的统计信息
     * @param file Excel文件
     * @return 统计信息，包含工作表数量、总行数等
     * @throws ExcelParseException 当解析失败时抛出异常
     */
    @Throws(ExcelParseException::class)
    fun getExcelStatistics(file: File): ExcelStatistics
}

/**
 * Excel解析异常类
 * @param message 错误消息
 * @param cause 原因异常
 */
class ExcelParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Excel文件统计信息
 * @param totalSheets 工作表总数
 * @param totalRows 总行数
 * @param validWordCount 有效单词数量
 * @param invalidWordCount 无效单词数量
 */
data class ExcelStatistics(
    val totalSheets: Int,
    val totalRows: Int,
    val validWordCount: Int,
    val invalidWordCount: Int
) {
    /**
     * 获取总单词数量
     */
    val totalWordCount: Int
        get() = validWordCount + invalidWordCount
    
    /**
     * 获取数据有效性百分比
     */
    val validityPercentage: Float
        get() = if (totalWordCount > 0) {
            (validWordCount.toFloat() / totalWordCount) * 100
        } else {
            0f
        }
}