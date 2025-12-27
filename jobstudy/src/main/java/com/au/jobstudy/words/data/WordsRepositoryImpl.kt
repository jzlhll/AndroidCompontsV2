package com.au.jobstudy.words.data

import com.au.jobstudy.words.data.dao.WordsDao
import com.au.jobstudy.words.domain.IWordRepository
import com.au.jobstudy.words.domain.beans.DBTableMode
import com.au.jobstudy.words.domain.beans.RowInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WordsRepositoryImpl()
        : IWordRepository, KoinComponent {
    private val wordsDao: WordsDao by inject()
    /** 查询全部函数
     * @param mode 如果是null，则返回所有数据; 如果是非null，则返回指定模式下的数据(由于几个Word相同，故而相同)
     * */
    override suspend fun getAllRows(mode: DBTableMode?): List<RowInfo> {
        val allRows = mutableListOf<RowInfo>()

        // 获取所有MudRow
        if (mode == null || mode == DBTableMode.Mud) {
            allRows.addAll(
                wordsDao.getAllMudRows().map {
                    RowInfo.MudRow(word = it.word, sheetName = it.sheetName, lines = it.lines)
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
                        sheetName = it.sheetName,
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
                        sheetName = it.sheetName,
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
    override suspend fun getWord(word: String): List<RowInfo.WordRow> {
        val rows = mutableListOf<RowInfo.WordRow>()
        // 查询WordRow
        rows.addAll(
            wordsDao.getWordRowByWord(word).map {
                RowInfo.WordRow(
                    word = it.word,
                    phonetic = it.phonetic,
                    sheetName = it.sheetName,
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
    override suspend fun getQuestion(word: String): List<RowInfo.QuestionRow> {
        val rows = mutableListOf<RowInfo.QuestionRow>()
        // 获取所有QuestionRow
        rows.addAll(
            wordsDao.getQuestionRowByWord(word).map {
                RowInfo.QuestionRow(
                    word = it.word,
                    function = it.function,
                    sheetName = it.sheetName,
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
    override suspend fun getMud(word: String): List<RowInfo.MudRow> {
        val rows = mutableListOf<RowInfo.MudRow>()
        // 获取所有MudRow
        rows.addAll(
            wordsDao.getMudRowByWord(word).map {
                RowInfo.MudRow(
                    word = it.word,
                    sheetName = it.sheetName,
                    lines = it.lines
                )
            }
        )
        return rows
    }
}