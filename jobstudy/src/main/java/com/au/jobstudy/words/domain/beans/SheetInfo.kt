package com.au.jobstudy.words.domain.beans

/**
 * Excel表格信息
 */
data class SheetInfo(val sheetName: String,
                     val index: Int,
                     val sheetMode: SheetMode,
                     val rowCellNum:Int)