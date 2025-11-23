package com.au.jobstudy.words.data.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "word_row")
data class WordRowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val phonetic: String,
    val meaning: String,
    val sheetName:String,
    val category: String = "",
    val sentence: String,
    val sentence2: String = "",
    val sentence3: String = "",
    val grama: String = "",
    val imageUrl: String = ""
)