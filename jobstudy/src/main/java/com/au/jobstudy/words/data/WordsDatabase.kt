package com.au.jobstudy.words.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.au.jobstudy.words.data.dao.WordsDao
import com.au.jobstudy.words.data.entities.ImportVersionEntity
import com.au.jobstudy.words.data.entities.MudRowEntity
import com.au.jobstudy.words.data.entities.QuestionRowEntity
import com.au.jobstudy.words.data.entities.WordRowEntity
import com.au.module_android.Globals

@Database(
    entities = [
        MudRowEntity::class,
        QuestionRowEntity::class,
        WordRowEntity::class,
        ImportVersionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WordsDatabase : RoomDatabase() {
    abstract fun wordsDao(): WordsDao
}