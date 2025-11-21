package com.au.jobstudy.words

import android.content.Context
import com.au.jobstudy.words.loading.ApachePoiExcelParser
import com.au.jobstudy.words.loading.IExcelParser

object WordsManager {
    fun createExcelParser(context: Context): IExcelParser {
        return ApachePoiExcelParser(context)
    }
}