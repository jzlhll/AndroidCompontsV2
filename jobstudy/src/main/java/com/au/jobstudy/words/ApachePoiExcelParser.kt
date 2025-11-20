package com.au.jobstudy.words

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

/**
 * 使用Apache POI实现的Excel解析器
 * 支持.xlsx和.xls格式的Excel文件解析
 */
class ApachePoiExcelParser : ExcelParser {
    
    companion object {
        private const val TAG = "ApachePoiExcelParser"
        private const val HEADER_ROW_INDEX = 0
        
        // Excel文件列索引定义（假设Excel文件列顺序为：单词、音标、解释、造句、类别）
        private const val COLUMN_WORD = 0
        private const val COLUMN_PHONETIC = 1
        private const val COLUMN_MEANING = 2
        private const val COLUMN_SENTENCE = 3
        private const val COLUMN_CATEGORY = 4
    }
    
    private var context: Context? = null
    
    override fun initialize(context: Context) {
        this.context = context
    }
    
    override fun getAllSheetNames(file: File): List<String> {
        if (!isValidExcelFile(file)) {
            throw ExcelParseException("无效的Excel文件: ${file.absolutePath}")
        }
        
        return try {
            FileInputStream(file).use { fis ->
                WorkbookFactory.create(fis).use { workbook ->
                    (0 until workbook.numberOfSheets).map { index ->
                        val sheet = workbook.getSheetAt(index)
                        val sheetName = sheet.sheetName
                        Log.d(TAG, "找到工作表: $sheetName")
                        sheetName
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取工作表名称失败", e)
            throw ExcelParseException("读取工作表名称失败: ${e.message}", e)
        }
    }
    
    override fun getAllRowsFromSheet(file: File, sheetName: String): List<List<String>> {
        if (!isValidExcelFile(file)) {
            throw ExcelParseException("无效的Excel文件: ${file.absolutePath}")
        }
        
        return try {
            FileInputStream(file).use { fis ->
                WorkbookFactory.create(fis).use { workbook ->
                    val sheet = workbook.getSheet(sheetName)
                        ?: throw ExcelParseException("未找到工作表: $sheetName")
                    
                    val rows = mutableListOf<List<String>>()
                    
                    for (rowIndex in sheet.firstRowNum..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex)
                        if (row != null) {
                            val rowData = mutableListOf<String>()
                            
                            for (cellIndex in row.firstCellNum until row.lastCellNum) {
                                val cell = row.getCell(cellIndex)
                                val cellValue = when (cell?.cellType) {
                                    CellType.STRING -> cell.stringCellValue
                                    CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) {
                                        cell.dateCellValue.toString()
                                    } else {
                                        cell.numericCellValue.toString()
                                    }
                                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                    CellType.FORMULA -> cell.cellFormula
                                    CellType.BLANK -> ""
                                    else -> ""
                                }
                                rowData.add(cellValue)
                            }
                            rows.add(rowData)
                        }
                    }
                    
                    Log.d(TAG, "从工作表 '$sheetName' 读取到 ${rows.size} 行数据")
                    rows
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取工作表 '$sheetName' 失败", e)
            throw ExcelParseException("读取工作表 '$sheetName' 失败: ${e.message}", e)
        }
    }
    
    override fun getAllWordsFromSheet(file: File, sheetName: String): List<WordBean> {
        val allRows = getAllRowsFromSheet(file, sheetName)
        val words = mutableListOf<WordBean>()
        
        // 跳过标题行
        val dataRows = if (allRows.size > HEADER_ROW_INDEX) {
            allRows.drop(HEADER_ROW_INDEX + 1)
        } else {
            allRows
        }
        
        for ((index, rowData) in dataRows.withIndex()) {
            try {
                val wordBean = convertRowToWordBean(rowData)
                if (wordBean.isValid()) {
                    words.add(wordBean)
                } else {
                    Log.w(TAG, "第 ${index + HEADER_ROW_INDEX + 2} 行数据无效，跳过: $rowData")
                }
            } catch (e: ExcelParseException) {
                Log.w(TAG, "转换第 ${index + HEADER_ROW_INDEX + 2} 行数据失败", e)
            }
        }
        
        Log.d(TAG, "从工作表 '$sheetName' 成功解析 ${words.size} 个有效单词")
        return words
    }
    
    override fun convertRowToWordBean(rowData: List<String>): WordBean {
        if (rowData.isEmpty()) {
            throw ExcelParseException("行数据为空")
        }
        
        // 确保行数据有足够的列
        val paddedRowData = rowData + List(maxOf(0, COLUMN_CATEGORY + 1 - rowData.size)) { "" }
        
        val word = paddedRowData.getOrNull(COLUMN_WORD)?.trim() ?: ""
        val phonetic = paddedRowData.getOrNull(COLUMN_PHONETIC)?.trim() ?: ""
        val meaning = paddedRowData.getOrNull(COLUMN_MEANING)?.trim() ?: ""
        val sentence = paddedRowData.getOrNull(COLUMN_SENTENCE)?.trim() ?: ""
        val category = paddedRowData.getOrNull(COLUMN_CATEGORY)?.trim() ?: ""
        
        // 验证必需字段
        if (word.isBlank()) {
            throw ExcelParseException("单词字段不能为空")
        }
        
        if (meaning.isBlank()) {
            throw ExcelParseException("释义字段不能为空")
        }
        
        return WordBean(
            word = word,
            phonetic = phonetic,
            meaning = meaning,
            sentence = sentence,
            category = category
        )
    }
    
    override fun isValidExcelFile(file: File): Boolean {
        return try {
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "文件不存在或不可读: ${file.absolutePath}")
                return false
            }
            
            // 检查文件扩展名
            val fileName = file.name.lowercase()
            if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
                Log.w(TAG, "不支持的文件格式: $fileName")
                return false
            }
            
            // 尝试打开文件
            FileInputStream(file).use { fis ->
                WorkbookFactory.create(fis).use { workbook ->
                    Log.d(TAG, "成功打开Excel文件: ${file.absolutePath}")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "验证Excel文件失败: ${file.absolutePath}", e)
            false
        }
    }
    
    override fun getExcelStatistics(file: File): ExcelStatistics {
        val sheetNames = getAllSheetNames(file)
        var totalRows = 0
        var validWordCount = 0
        var invalidWordCount = 0
        
        for (sheetName in sheetNames) {
            val allRows = getAllRowsFromSheet(file, sheetName)
            totalRows += allRows.size
            
            // 跳过标题行，计算数据行
            val dataRows = if (allRows.size > HEADER_ROW_INDEX) {
                allRows.drop(HEADER_ROW_INDEX + 1)
            } else {
                allRows
            }
            
            for (rowData in dataRows) {
                try {
                    val wordBean = convertRowToWordBean(rowData)
                    if (wordBean.isValid()) {
                        validWordCount++
                    } else {
                        invalidWordCount++
                    }
                } catch (e: Exception) {
                    invalidWordCount++
                }
            }
        }
        
        return ExcelStatistics(
            totalSheets = sheetNames.size,
            totalRows = totalRows,
            validWordCount = validWordCount,
            invalidWordCount = invalidWordCount
        )
    }
}