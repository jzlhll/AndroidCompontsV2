package com.au.jobstudy.words.domain

import android.content.Context
import com.au.jobstudy.words.domain.beans.RowInfo
import com.au.jobstudy.words.domain.beans.RowOrigData
import com.au.jobstudy.words.domain.beans.SheetInfo
import com.au.jobstudy.words.ui.ExcelParseException
import com.au.module_android.log.logEx
import com.au.module_android.log.logd
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.IOException
import java.io.InputStream

/**
 * Apache POI实现的Excel解析器
 */
class ApachePoiExcelParser(private val context: Context) : IExcelParser {
    private val TAG = "ApachePoiExcelParser"
    private var workbook: Workbook? = null
    private var mInputStream: InputStream? = null
    
    /**
     * 从assets加载Excel文件并获取所有工作表信息
     */
    override fun loadExcelFromAssets(fileName: String): List<SheetInfo> {
        try {
            // 关闭之前的工作簿
            workbook?.close()
            
            // 打开assets中的Excel文件
            val inputStream: InputStream = context.assets.open(fileName)
            this.mInputStream = inputStream
            
            // 根据文件扩展名创建相应的工作簿
            workbook = if (fileName.endsWith(".xlsx")) {
                XSSFWorkbook(inputStream)
            } else if (fileName.endsWith(".xls")) {
                HSSFWorkbook(inputStream)
            } else {
                throw IllegalArgumentException("Unsupported file format: $fileName")
            }
            
            // 获取所有工作表信息
            val sheetInfos = mutableListOf<SheetInfo>()
            for (i in 0 until workbook!!.numberOfSheets) {
                val sheetName = workbook!!.getSheetName(i)
                val sheet = workbook!!.getSheetAt(i)
                val headRow = sheet.getRow(0)
                if (headRow != null) {
                    val rowOrigData = parseRowDataHead(row = headRow)
                    val sheetMode = RowInfo.parseRowHead(rowOrigData)
                    sheetInfos.add(SheetInfo(sheetName, i, sheetMode, rowOrigData.rowCellNum))
                }
            }
            logd(TAG) { "Loaded Excel file: $fileName with ${sheetInfos.size} sheets" }
            return sheetInfos
        } catch (e: IOException) {
            logEx(tag=TAG, throwable = e) { "Error loading Excel file from assets: $fileName" }
            throw ExcelParseException("Failed to load Excel file: $fileName", e)
        } catch (e: Exception) {
            logEx(TAG, throwable = e) { "Error processing Excel file: $fileName" }
            throw ExcelParseException("Error processing Excel file: $fileName", e)
        }
    }
    
    /**
     * 解析指定的工作表并返回所有行数据
     */
    override fun parseSheet(sheetInfo: SheetInfo): List<RowOrigData> {
        try {
            if (workbook == null) {
                throw IllegalStateException("Workbook not initialized. Call loadExcelFromAssets first.")
            }
            
            val sheet = workbook!!.getSheetAt(sheetInfo.index)
            val rowDataList = mutableListOf<RowOrigData>()
            
            // 获取最大行号和最大列号
            val lastRowNum = sheet.lastRowNum
            
            for (rowIndex in 0..lastRowNum) {
                val row = sheet.getRow(rowIndex)
                if (row != null) {
                    val rowOrigData = parseRowData(rowIndex, row, sheetInfo.rowCellNum)
                    if (rowOrigData != null) {
                        rowDataList.add(rowOrigData)
                    }
                }
            }
            
            logd(TAG) { "Parsed sheet '${sheetInfo.sheetName}' with ${rowDataList.size} rows" }
            return rowDataList
            
        } catch (e: Exception) {
            logEx(TAG, throwable = e) { "Error parsing sheet: ${sheetInfo.sheetName}" }
            throw ExcelParseException("Failed to parse sheet: ${sheetInfo.sheetName}", e)
        }
    }
    
    /**
     * 解析单行Excel数据
     * @param rowIndex 行索引
     * @param row Excel行对象
     * @return 解析后的RowOrigData对象，如果为空行则返回null
     */
    private fun parseRowData(rowIndex: Int, row: Row, rowCellNum:Int): RowOrigData? {
        val values = mutableListOf<String>()
        
        for (cellIndex in 0 until rowCellNum) {
            val cell = row.getCell(cellIndex)
            values.add(cell?.let { getCellValue(it) } ?: "")
        }
        
        // 判断是否为空行（所有单元格都为空）
        val isEmptyRow = values.all { it.isBlank() }
        if (!isEmptyRow) {
            // 假设第一行为表头
            val isTabRow = rowIndex == 0
            return RowOrigData(rowIndex, values, rowCellNum, isTabRow)
        }
        
        return null
    }

    private fun parseRowDataHead(rowIndex: Int = 0, row: Row): RowOrigData {
        val values = mutableListOf<String>()

        val lastCellNum = row.lastCellNum
        for (cellIndex in 0 until lastCellNum) {
            val cell = row.getCell(cellIndex)
            val v = cell?.let { getCellValue(it) }
            if (!v.isNullOrBlank()) {
                values.add(v)
            } else {
                break //为空了就跳出了。
            }
        }
        // 判断是否为空行（所有单元格都为空）
        return RowOrigData(rowIndex, values, values.size, true)
    }
    
    /**
     * 获取单元格的值作为字符串
     */
    private fun getCellValue(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                // 检查是否是日期格式
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue.toString()
                } else {
                    // 转换数字为字符串，避免科学计数法
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        value.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    val cellValue = cell.cellFormula
                    // 尝试获取公式计算结果
                    when (cell.cachedFormulaResultType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        else -> cellValue
                    }
                } catch (e: Exception) {
                    // 如果无法获取公式结果，返回公式本身
                    cell.cellFormula
                }
            }
            else -> ""
        }
    }
    
    /**
     * 关闭工作簿资源
     */
    override fun close() {
        try {
            workbook?.close()
            mInputStream?.close()
            workbook = null
            logd(TAG) { "Workbook closed" }
        } catch (e: IOException) {
            logEx(TAG, throwable = e) { "Error closing workbook" }
        }
    }
}

