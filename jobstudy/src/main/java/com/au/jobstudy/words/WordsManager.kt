package com.au.jobstudy.words

import android.content.Context
import com.au.jobstudy.words.domain.beans.RowInfo
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

    /**
     * 获取asset下的excel版本信息
     */
    suspend fun assetExcelVersion(context: Context) : Int{
        context.assets.open("小学英语总结.version").use {
            return it.read().toInt()
        }

    }
}