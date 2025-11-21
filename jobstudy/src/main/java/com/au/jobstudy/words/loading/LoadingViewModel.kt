package com.au.jobstudy.words.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.jobstudy.words.WordsManager
import com.au.jobstudy.words.db.Constants.Companion.XLSX_NAME
import com.au.module_android.Globals
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utils.logd
import kotlinx.coroutines.flow.MutableSharedFlow

class LoadingViewModel : ViewModel() {
    private val TAG = "LoadingViewModel"

    private val excelParser = WordsManager.createExcelParser(Globals.app)
    val overFlow = MutableSharedFlow<Boolean>()

    fun load() {
        viewModelScope.launchOnIOThread {
            val sheetInfos = excelParser.loadExcelFromAssets(XLSX_NAME)
            for (sheetInfo in sheetInfos) {
                logd(TAG) { "${sheetInfo.sheetName} ${sheetInfo.index}" }
                val rows = excelParser.parseSheet(sheetInfo)
                for (row in rows) {
                    val rowInfo = row.convert(row, sheetInfo.sheetMode)
                    logd(TAG) { "$rowInfo" }
                }
            }
        }
    }
}