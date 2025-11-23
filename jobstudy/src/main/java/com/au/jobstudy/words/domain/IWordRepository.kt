package com.au.jobstudy.words.domain

import com.au.jobstudy.words.domain.beans.DBTableMode
import com.au.jobstudy.words.domain.beans.RowInfo

interface IWordRepository{
    /** 查询全部函数
     * @param mode 如果是null，则返回所有数据; 如果是非null，则返回指定模式下的数据(由于几个Word相同，故而相同)
     * */
    suspend fun getAllRows(mode: DBTableMode? = null): List<RowInfo>

    /** 按照word查询函数
     */
    suspend fun getWord(word: String): List<RowInfo.WordRow>

    /** 按照word查询函数
     */
    suspend fun getQuestion(word: String): List<RowInfo.QuestionRow>

    /** 按照word查询函数
     */
    suspend fun getMud(word: String): List<RowInfo.MudRow>
}