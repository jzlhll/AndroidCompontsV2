package com.au.jobstudy.words.constants

import android.content.Context
import androidx.annotation.WorkerThread
import com.au.jobstudy.words.domain.beans.RowInfo
import com.au.jobstudy.words.domain.ApachePoiExcelParser
import com.au.jobstudy.words.domain.IExcelParser

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
    @WorkerThread
    fun assetExcelVersion(context: Context) : Int{
        context.assets.open("小学英语总结.version").use {
            return it.read()
        }
    }
}