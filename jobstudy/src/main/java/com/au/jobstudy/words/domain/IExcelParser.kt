package com.au.jobstudy.words.domain

import com.au.jobstudy.words.domain.beans.RowOrigData
import com.au.jobstudy.words.domain.beans.SheetInfo

interface IExcelParser {
    fun loadExcelFromAssets(fileName: String) : List<SheetInfo>
    fun parseSheet(sheetInfo: SheetInfo) : List<RowOrigData>
    fun close()
}