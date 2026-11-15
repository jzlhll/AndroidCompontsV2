package com.allan.picnote.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.allan.picnote.data.dao.PicNoteDao
import com.allan.picnote.data.entity.*

@Database(
    entities = [DocumentEntity::class, NodeEntity::class, NodeImageEntity::class, SourceImageEntity::class, MediaSnapshotEntity::class, MediaScanStateEntity::class, MediaChangeEntity::class, PendingMediaEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PicNoteDatabase : RoomDatabase() {
    abstract fun dao(): PicNoteDao
}
