package com.au.jobstudy.words.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.au.jobstudy.words.db.entities.ImportVersionEntity
import com.au.jobstudy.words.db.entities.MudRowEntity
import com.au.jobstudy.words.db.entities.QuestionRowEntity
import com.au.jobstudy.words.db.entities.WordRowEntity

@Dao
interface WordsDao {
    // MudRow相关操作
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMudRows(rows: List<MudRowEntity>)
    
    @Query("SELECT * FROM mud_row")
    suspend fun getAllMudRows(): List<MudRowEntity>
    
    @Query("SELECT * FROM mud_row WHERE word = :word")
    suspend fun getMudRowByWord(word: String): List<MudRowEntity>
    
    // QuestionRow相关操作
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionRows(rows: List<QuestionRowEntity>)
    
    @Query("SELECT * FROM question_row")
    suspend fun getAllQuestionRows(): List<QuestionRowEntity>
    
    @Query("SELECT * FROM question_row WHERE word = :word")
    suspend fun getQuestionRowByWord(word: String): List<QuestionRowEntity>
    
    // WordRow相关操作
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordRows(rows: List<WordRowEntity>)
    
    @Query("SELECT * FROM word_row")
    suspend fun getAllWordRows(): List<WordRowEntity>
    
    @Query("SELECT * FROM word_row WHERE word = :word")
    suspend fun getWordRowByWord(word: String): List<WordRowEntity>
    
    // ImportVersion相关操作
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportVersion(version: ImportVersionEntity): Long
    
    @Query("SELECT * FROM import_version ORDER BY importTime DESC LIMIT 1")
    suspend fun getLatestImportVersion(): ImportVersionEntity?
    
    @Query("DELETE FROM import_version WHERE id NOT IN (SELECT id FROM import_version ORDER BY importTime DESC LIMIT 5)")
    suspend fun deleteOldVersions()
}