package com.au.jobstudy.words.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_version")
data class ImportVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val versionCode: Int,
    val importTime: Long,
    val description: String? = null
)