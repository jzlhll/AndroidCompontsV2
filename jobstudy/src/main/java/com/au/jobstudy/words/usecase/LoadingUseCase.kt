package com.au.jobstudy.words.usecase

import com.au.jobstudy.words.constants.Constants.Companion.XLSX_NAME
import com.au.jobstudy.words.constants.WordsManager
import com.au.jobstudy.words.data.entities.ImportVersionEntity
import com.au.jobstudy.words.domain.IImportExcelRepository
import com.au.jobstudy.words.domain.IWordRepository
import com.au.jobstudy.words.domain.beans.DBTableMode
import com.au.jobstudy.words.domain.beans.RowInfo
import com.au.module_android.Globals
import com.au.module_android.log.logd
import com.au.module_android.log.logdNoFile

class LoadingUseCase(
    val wordsManager: WordsManager,
    val importExcelRepository: IImportExcelRepository,
//    val loadingTest: LoadingTest,
    val wordRepository: IWordRepository) {

    private val excelParser = wordsManager.createExcelParser(Globals.app)

    /**
     * 检查导入excel原始数据
     */
    suspend fun checkAndImportExcel() {
        val repo = importExcelRepository
        val versionInfo = repo.importVersionInfo()
        var need = false
        val version = wordsManager.assetExcelVersion(Globals.app)

        if (versionInfo == null) {
            need = true
        } else {
            logdNoFile { "check & ImportExcel version $version" }
            if (version > versionInfo.versionCode) {
                need = true
            }
        }

        if (need) {
            loadFromExcel()
            repo.importVersion(ImportVersionEntity(versionCode = version, importTime = System.currentTimeMillis()))
        } else {
            loadFromDB()
        }
    }

    private suspend fun loadFromExcel() {
        val sheetInfos = excelParser.loadExcelFromAssets(XLSX_NAME)

        val allRows = mutableListOf<RowInfo>()
        val allSingleWords = mutableListOf<RowInfo.WordRow>()
        val allQuestionWords = mutableListOf<RowInfo.QuestionRow>()
        val allMudWords = mutableListOf<RowInfo.MudRow>()

        val sheetMappingRows = mutableMapOf<String, RowInfo>()

        for (sheetInfo in sheetInfos) {
            logd{ "${sheetInfo.sheetName} ${sheetInfo.index}" }
            val rows = excelParser.parseSheet(sheetInfo)
            for (row in rows) {
                when (val rowInfo = row.convert(row, sheetInfo.sheetMode, sheetInfo.sheetName)) {
                    is RowInfo.WordRow -> {
                        allSingleWords.add(rowInfo)
                        allRows.add(rowInfo)
                        sheetMappingRows[sheetInfo.sheetName] = rowInfo
                    }
                    is RowInfo.QuestionRow -> {
                        allQuestionWords.add(rowInfo)
                        allRows.add(rowInfo)
                        sheetMappingRows[sheetInfo.sheetName] = rowInfo
                    }
                    is RowInfo.MudRow -> {
                        allMudWords.add(rowInfo)
                        allRows.add(rowInfo)
                        sheetMappingRows[sheetInfo.sheetName] = rowInfo
                    }

                    is RowInfo.UnableRow -> {
                        // do nothing
                    }
                }
            }
        }

        wordsManager.allMudWords = allMudWords
        wordsManager.allQuestionWords = allQuestionWords
        wordsManager.allSingleWords = allSingleWords
        wordsManager.sheetMappingRows = sheetMappingRows

        importExcelRepository.importAllRows(allRows)
    }

    private suspend fun loadFromDB() {
        val repo = wordRepository
        logdNoFile { "repo $repo" }
        val allSingleWords = repo.getAllRows(DBTableMode.Word).map {
            it as RowInfo.WordRow
        }
        val allQuestionWords = repo.getAllRows(DBTableMode.Question).map {
            it as RowInfo.QuestionRow
        }
        val allMudWords = repo.getAllRows(DBTableMode.Mud).map {
            it as RowInfo.MudRow
        }
        val sheetMappingRows = repo.getAllRows()
        val sheetMappingRowsMap = sheetMappingRows.associateBy { it.sheetName }
        wordsManager.allMudWords = allMudWords
        wordsManager.allQuestionWords = allQuestionWords
        wordsManager.allSingleWords = allSingleWords
        wordsManager.sheetMappingRows = sheetMappingRowsMap
    }

    fun close() {
        excelParser.close()
        logdNoFile { "loadingTest close 11" }
//        loadingTest.add()
    }
}