package com.au.jobstudy.check

import androidx.room.Database
import androidx.room.RoomDatabase
import com.au.jobstudy.check.bean.CompletedEntity
import com.au.jobstudy.check.bean.StarEntity
import com.au.jobstudy.check.bean.WorkEntity
import com.au.jobstudy.check.dao.CompletedDao
import com.au.jobstudy.check.dao.StarDao
import com.au.jobstudy.check.dao.WorkDao

@Database(entities = [WorkEntity::class,
    CompletedEntity::class,
    StarEntity::class],
    version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getWorkDao(): WorkDao
    abstract fun getCompletedDao(): CompletedDao
    abstract fun getStarDao(): StarDao
}