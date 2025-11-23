package com.au.jobstudy.words.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.jobstudy.words.WordsManager
import com.au.jobstudy.words.domain.beans.RowInfo
import com.au.jobstudy.words.data.Constants.Companion.XLSX_NAME
import com.au.jobstudy.words.data.WordsRepositoryImpl
import com.au.jobstudy.words.data.entities.ImportVersionEntity
import com.au.module_android.Globals
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LoadingViewModel : ViewModel() {
    private val TAG = "LoadingViewModel"

    private val excelParser = WordsManager.createExcelParser(Globals.app)
    // 设置replay=1确保即使在emit后collect也能收到最新的事件
    private val _overFlow = MutableSharedFlow<Boolean>()
    val overFlow = _overFlow.asSharedFlow()

    /**
     * 检查导入excel原始数据
     */
    fun checkAndImportExcel() {
        viewModelScope.launchOnThread {
            val repo = WordsRepositoryImpl()
            if (!repo.isImported()) {
                load()
                repo.importVersion(ImportVersionEntity(versionCode = 1, importTime = System.currentTimeMillis()))
            }
        }
    }

   suspend fun load() {
        val sheetInfos = excelParser.loadExcelFromAssets(XLSX_NAME)

        val allSingleWords = mutableListOf<RowInfo.WordRow>()
        val allQuestionWords = mutableListOf<RowInfo.QuestionRow>()
        val allMudWords = mutableListOf<RowInfo.MudRow>()

        val sheetMappingRows = mutableMapOf<String, RowInfo>()

        for (sheetInfo in sheetInfos) {
            logd(TAG) { "${sheetInfo.sheetName} ${sheetInfo.index}" }
            val rows = excelParser.parseSheet(sheetInfo)
            for (row in rows) {
                //                    logd(TAG) { "$rowInfo" }
                when (val rowInfo = row.convert(row, sheetInfo.sheetMode)) {
                    is RowInfo.WordRow -> {
                        allSingleWords.add(rowInfo)
                        sheetMappingRows[sheetInfo.sheetName] = rowInfo
                    }
                    is RowInfo.QuestionRow -> {
                        allQuestionWords.add(rowInfo)
                        sheetMappingRows[sheetInfo.sheetName] = rowInfo
                    }
                    is RowInfo.MudRow -> {
                        allMudWords.add(rowInfo)
                        sheetMappingRows[sheetInfo.sheetName] = rowInfo
                    }

                    is RowInfo.UnableRow -> {
                        // do nothing
                    }
                }
            }
        }

        WordsManager.allMudWords = allMudWords
        WordsManager.allQuestionWords = allQuestionWords
        WordsManager.allSingleWords = allSingleWords
        WordsManager.sheetMappingRows = sheetMappingRows

        // 使用emit代替tryEmit，确保事件被发送
        _overFlow.emit(true)
    }
}