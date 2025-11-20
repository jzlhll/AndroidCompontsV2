package com.au.jobstudy.words

import android.content.Context

/**
 * 单词解析器管理器
 * 统一管理Excel解析器实例，提供便捷的使用方法
 */
object WordParserManager {
    
    private var excelParser: ExcelParser? = null
    private var context: Context? = null
    
    /**
     * 初始化解析器管理器
     * @param context Android上下文
     */
    fun initialize(context: Context) {
        this.context = context
        this.excelParser = ApachePoiExcelParser().apply {
            initialize(context)
        }
    }
    
    /**
     * 获取Excel解析器实例
     */
    fun getExcelParser(): ExcelParser {
        return excelParser ?: throw IllegalStateException("解析器未初始化，请先调用 initialize() 方法")
    }
    
    /**
     * 从Excel文件加载所有单词数据
     * @param excelFile Excel文件
     * @param sheetName 工作表名称，如果为null则加载第一个工作表
     * @return 单词Bean列表
     */
    fun loadWordsFromExcel(excelFile: java.io.File, sheetName: String? = null): List<WordBean> {
        val parser = getExcelParser()
        
        // 获取要解析的工作表名称
        val targetSheetName = sheetName ?: run {
            val sheetNames = parser.getAllSheetNames(excelFile)
            if (sheetNames.isEmpty()) {
                throw ExcelParseException("Excel文件中没有工作表")
            }
            sheetNames.first()
        }
        
        return parser.getAllWordsFromSheet(excelFile, targetSheetName)
    }
    
    /**
     * 获取Excel文件的统计信息
     * @param excelFile Excel文件
     * @return 统计信息
     */
    fun getExcelStatistics(excelFile: java.io.File): ExcelStatistics {
        return getExcelParser().getExcelStatistics(excelFile)
    }
    
    /**
     * 获取Excel文件的所有工作表名称
     * @param excelFile Excel文件
     * @return 工作表名称列表
     */
    fun getSheetNames(excelFile: java.io.File): List<String> {
        return getExcelParser().getAllSheetNames(excelFile)
    }
    
    /**
     * 验证Excel文件是否有效
     * @param excelFile Excel文件
     * @return 是否有效
     */
    fun isValidExcelFile(excelFile: java.io.File): Boolean {
        return getExcelParser().isValidExcelFile(excelFile)
    }
}