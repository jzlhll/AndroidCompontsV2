package com.au.jobstudy.words.db

import com.au.jobstudy.words.beans.DBTableMode
import com.au.jobstudy.words.beans.RowInfo
import com.au.jobstudy.words.db.dao.WordsDao
import com.au.jobstudy.words.db.entities.ImportVersionEntity
import com.au.jobstudy.words.db.entities.MudRowEntity
import com.au.jobstudy.words.db.entities.QuestionRowEntity
import com.au.jobstudy.words.db.entities.WordRowEntity

class WordsRepository(private val wordsDao: WordsDao = WordsDatabase.db.wordsDao()) {
    suspend fun isImported() : Boolean {
        val latestVersion = wordsDao.getLatestImportVersion()
        return latestVersion != null
    }

    suspend fun importVersionInfo() : ImportVersionEntity? {
        return wordsDao.getLatestImportVersion()
    }

    suspend fun importVersion(version: ImportVersionEntity) {
        wordsDao.insertImportVersion(version)
        wordsDao.deleteOldVersions()
    }

    /** 批量导入函数 */
    suspend fun importAllRows(rows: List<RowInfo>) {
        val mudRows = mutableListOf<MudRowEntity>()
        val questionRows = mutableListOf<QuestionRowEntity>()
        val wordRows = mutableListOf<WordRowEntity>()
        
        rows.forEach {row ->
            when (row) {
                is RowInfo.MudRow -> {
                    mudRows.add(MudRowEntity(word = row.word, lines = row.lines))
                }
                is RowInfo.QuestionRow -> {
                    questionRows.add(
                        QuestionRowEntity(
                            word = row.word,
                            function = row.function,
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
    
    /** 查询全部函数
     * @param mode 如果是null，则返回所有数据; 如果是非null，则返回指定模式下的数据(由于几个Word相同，故而相同)
     * */
    suspend fun getAllRows(mode: DBTableMode? = null): List<RowInfo> {
        val allRows = mutableListOf<RowInfo>()

        // 获取所有MudRow
        if (mode == null || mode == DBTableMode.Mud) {
            allRows.addAll(
                wordsDao.getAllMudRows().map {
                    RowInfo.MudRow(word = it.word, lines = it.lines)
                }
            )
        }

        // 获取所有QuestionRow
        if (mode == null || mode == DBTableMode.Question) {
            allRows.addAll(
                wordsDao.getAllQuestionRows().map {
                    RowInfo.QuestionRow(
                        word = it.word,
                        function = it.function,
                        sentence = it.sentence,
                        sentence2 = it.sentence2,
                        sentence3 = it.sentence3,
                        sentence4 = it.sentence4,
                        sentence5 = it.sentence5
                    )
                }
            )
        }

        // 获取所有WordRow
        if (mode == null
            || mode == DBTableMode.Word) {
            allRows.addAll(
                wordsDao.getAllWordRows().map {
                    RowInfo.WordRow(
                        word = it.word,
                        phonetic = it.phonetic,
                        meaning = it.meaning,
                        category = it.category,
                        sentence = it.sentence,
                        sentence2 = it.sentence2,
                        sentence3 = it.sentence3,
                        grama = it.grama,
                        imageUrl = it.imageUrl
                    )
                }
            )
        }
        return allRows
    }

    /** 按照word查询函数
     */
    suspend fun getWord(word: String): List<RowInfo.WordRow> {
        val rows = mutableListOf<RowInfo.WordRow>()
        // 查询WordRow
        rows.addAll(
            wordsDao.getWordRowByWord(word).map {
                RowInfo.WordRow(
                    word = it.word,
                    phonetic = it.phonetic,
                    meaning = it.meaning,
                    category = it.category,
                    sentence = it.sentence,
                    sentence2 = it.sentence2,
                    sentence3 = it.sentence3,
                    grama = it.grama,
                    imageUrl = it.imageUrl
                )
            }
        )
        
        return rows
    }

    /** 按照word查询函数
     */
    suspend fun getQuestion(word: String): List<RowInfo.QuestionRow> {
        val rows = mutableListOf<RowInfo.QuestionRow>()
        // 获取所有QuestionRow
        rows.addAll(
            wordsDao.getQuestionRowByWord(word).map {
                RowInfo.QuestionRow(
                    word = it.word,
                    function = it.function,
                    sentence = it.sentence,
                    sentence2 = it.sentence2,
                    sentence3 = it.sentence3,
                    sentence4 = it.sentence4,
                    sentence5 = it.sentence5
                )
            }
        )
        return rows
    }

    /** 按照word查询函数
     */
    suspend fun getMud(word: String): List<RowInfo.MudRow> {
        val rows = mutableListOf<RowInfo.MudRow>()
        // 获取所有MudRow
        rows.addAll(
            wordsDao.getMudRowByWord(word).map {
                RowInfo.MudRow(
                    word = it.word,
                    lines = it.lines
                )
            }
        )
        return rows
    }
}