package com.au.jobstudy.words.domain.beans

import com.au.jobstudy.words.loading.IConvert
import com.au.module_android.utils.NoWayException

/**
 * 单行数据
 * @param rowIndex 行索引
 * @param values 单行数据内容
 * @param isRowHead 是否是列头
 */
data class RowOrigData(val rowIndex: Int,
                       val values: List<String>,
                       val rowCellNum:Int,
                       val isRowHead: Boolean = false,) : IConvert {
    /**
     * 转换一行数据为IRow
     */
    override fun convert(rowData: RowOrigData, sheetMode: SheetMode) : RowInfo {
        // 如果是表头行或空行，返回UnableRow
        if (rowData.isRowHead || rowData.values.isEmpty() || rowData.values.all { it.isBlank() }) {
            return RowInfo.UnableRow("")
        }

        return when (sheetMode) {
            SheetMode.Word4,
            SheetMode.Word5C,
             SheetMode.Word5P -> {
                // 单词表
                createWordRow(rowData, sheetMode)
            }
            SheetMode.Question -> {
                // 问答表
                createQuestionRow(rowData)
            }
            SheetMode.Mud -> {
                // 杂项表
                createMudRow(rowData)
            }
        }

    }

    private fun createWordRow(rowData: RowOrigData, sheetMode: SheetMode): RowInfo.WordRow {
        return when (sheetMode) {
            SheetMode.Word4 -> {
                RowInfo.WordRow(
                    word = rowData.values[0].trim(),
                    phonetic = rowData.values.getOrElse(1) { "" }.trim(),
                    meaning = rowData.values.getOrElse(2) { "" }.trim(),
                    sentence = rowData.values.getOrElse(3) { "" }.trim(),
                )
            }
            SheetMode.Word5C -> {
                RowInfo.WordRow(
                    word = rowData.values[0].trim(),
                    phonetic = rowData.values.getOrElse(1) { "" }.trim(),
                    meaning = rowData.values.getOrElse(2) { "" }.trim(),
                    category = rowData.values.getOrElse(3) { "" }.trim(),
                    sentence = rowData.values.getOrElse(4) { "" }.trim(),
                )
            }

            SheetMode.Word5P -> {
                RowInfo.WordRow(
                    word = rowData.values[0].trim(),
                    phonetic = rowData.values.getOrElse(1) { "" }.trim(),
                    meaning = rowData.values.getOrElse(2) { "" }.trim(),
                    sentence = rowData.values.getOrElse(3) { "" }.trim(),
                    grama = rowData.values.getOrElse(4) { "" }.trim(),
                )
            }

            else -> {
                throw NoWayException()
            }
        }
    }

    private fun createQuestionRow(rowData: RowOrigData): RowInfo.QuestionRow {
        // 检查第一列是否包含"?"或其他问答特征
        val firstCell = rowData.values[0].trim()
        return RowInfo.QuestionRow(
            word = firstCell,
            function = rowData.values.getOrElse(1) { "" }.trim(),
            sentence = rowData.values.getOrElse(2) { "" }.trim(),
            sentence2 = rowData.values.getOrElse(3) { "" }.trim(),
            sentence3 = rowData.values.getOrElse(4) { "" }.trim(),
            sentence4 = rowData.values.getOrElse(5) { "" }.trim(),
            sentence5 = rowData.values.getOrElse(6) { "" }.trim()
        )
    }
    
    /**
     * 创建杂项行，将所有单元格内容用换行符连接
     */
    private fun createMudRow(rowData: RowOrigData): RowInfo.MudRow {
        val word = rowData.values.firstOrNull()?.trim() ?: ""
        val lines = rowData.values.joinToString("\n") { it.trim() }
        return RowInfo.MudRow(word, lines)
    }
}