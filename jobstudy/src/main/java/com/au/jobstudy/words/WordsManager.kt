package com.au.jobstudy.words

import android.content.Context
import com.au.jobstudy.words.beans.RowInfo
import com.au.jobstudy.words.loading.ApachePoiExcelParser
import com.au.jobstudy.words.loading.IExcelParser

object WordsManager {
    var allSingleWords: List<RowInfo.WordRow> ?= null
    var allQuestionWords : List<RowInfo.QuestionRow>?= null
    var allMudWords: List<RowInfo.MudRow>?= null

    var sheetMappingRows : Map<String, RowInfo>?= null

    fun createExcelParser(context: Context): IExcelParser {
        return ApachePoiExcelParser(context)
    }
}