package com.au.jobstudy.words.loading

import com.au.jobstudy.words.beans.RowOrigData
import com.au.jobstudy.words.beans.SheetInfo

interface IExcelParser {
    fun loadExcelFromAssets(fileName: String) : List<SheetInfo>
    fun parseSheet(sheetInfo: SheetInfo) : List<RowOrigData>
    fun close()
}