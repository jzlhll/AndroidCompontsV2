package com.au.jobstudy.words.loading

import com.au.jobstudy.words.beans.RowInfo
import com.au.jobstudy.words.beans.RowOrigData
import com.au.jobstudy.words.beans.SheetMode

interface IConvert {
    /**
     * 转换一行数据为IRow
     */
    fun convert(rowData: RowOrigData, sheetMode: SheetMode) : RowInfo
}