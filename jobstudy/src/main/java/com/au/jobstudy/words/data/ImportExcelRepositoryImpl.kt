package com.au.jobstudy.words.data

import com.au.jobstudy.words.domain.beans.RowInfo
import com.au.jobstudy.words.data.dao.WordsDao
import com.au.jobstudy.words.data.entities.ImportVersionEntity
import com.au.jobstudy.words.data.entities.MudRowEntity
import com.au.jobstudy.words.data.entities.QuestionRowEntity
import com.au.jobstudy.words.data.entities.WordRowEntity
import com.au.jobstudy.words.domain.IImportExcelRepository

class ImportExcelRepositoryImpl(private val wordsDao: WordsDao = WordsDatabase.db.wordsDao())
        : IImportExcelRepository {
    override suspend fun isImported() : Boolean {
        val latestVersion = wordsDao.getLatestImportVersion()
        return latestVersion != null
    }

    override suspend fun importVersionInfo() : ImportVersionEntity? {
        return wordsDao.getLatestImportVersion()
    }

    override suspend fun importVersion(version: ImportVersionEntity) {
        wordsDao.insertImportVersion(version)
        wordsDao.deleteOldVersions()
    }

    /** 批量导入函数 */
    override suspend fun importAllRows(rows: List<RowInfo>) {
        val mudRows = mutableListOf<MudRowEntity>()
        val questionRows = mutableListOf<QuestionRowEntity>()
        val wordRows = mutableListOf<WordRowEntity>()
        
        rows.forEach {row ->
            when (row) {
                is RowInfo.MudRow -> {
                    val entity = MudRowEntity(word = row.word,
                        sheetName = row.sheetName,
                        lines = row.lines)
                    mudRows.add(entity)
                }
                is RowInfo.QuestionRow -> {
                    questionRows.add(
                        QuestionRowEntity(
                            word = row.word,
                            function = row.function,
                            sheetName = row.sheetName,
                            sentence = row.sentence,
                            sentence2 = row.sentence2,
                            sentence3 = row.sentence3,
                            sentence4 = row.sentence4,
                            sentence5 = row.sentence5
                        )
                    )
                }
                is RowInfo.WordRow -> {
                    wordRows.add(
                        WordRowEntity(
                            word = row.word,
                            phonetic = row.phonetic,
                            meaning = row.meaning,
                            sheetName = row.sheetName,
                            category = row.category,
                            sentence = row.sentence,
                            sentence2 = row.sentence2,
                            sentence3 = row.sentence3,
                            grama = row.grama,
                            imageUrl = row.imageUrl
                        )
                    )
                }
                else -> {}
            }
        }
        
        if (mudRows.isNotEmpty()) wordsDao.insertMudRows(mudRows)
        if (questionRows.isNotEmpty()) wordsDao.insertQuestionRows(questionRows)
        if (wordRows.isNotEmpty()) wordsDao.insertWordRows(wordRows)
    }
}