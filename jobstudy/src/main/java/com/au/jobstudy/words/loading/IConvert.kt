package com.au.jobstudy.words.loading

import com.au.jobstudy.words.domain.beans.RowInfo
import com.au.jobstudy.words.domain.beans.RowOrigData
import com.au.jobstudy.words.domain.beans.SheetMode

interface IConvert {
    /**
     * 转换一行数据为IRow
     */
    fun convert(rowData: RowOrigData, sheetMode: SheetMode) : RowInfo
}