package com.au.jobstudy.words.data.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(tableName = "question_row")
data class QuestionRowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val sheetName:String,
    val function: String,
    val sentence: String,
    val sentence2: String,
    val sentence3: String,
    val sentence4: String,
    val sentence5: String
)