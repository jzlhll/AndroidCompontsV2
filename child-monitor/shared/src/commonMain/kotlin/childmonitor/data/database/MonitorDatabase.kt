package childmonitor.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import childmonitor.data.database.dao.MonitorDao
import childmonitor.data.database.entity.*

/** 业务数据的唯一数据库；升级必须提供显式迁移，不允许破坏性回退。 */
@Database(entities = [SessionEntity::class, MediaEntity::class, ConfigRevisionEntity::class, EventEntity::class, CoverageEntity::class, RestWindowEntity::class, ReminderEntity::class, EvaluationEntity::class, MonitorSettingsEntity::class, OperationIntentEntity::class], version = 1, exportSchema = true)
@ConstructedBy(MonitorDatabaseConstructor::class)
abstract class MonitorDatabase : RoomDatabase() {
    abstract fun monitorDao(): MonitorDao
}

@Suppress("KotlinNoActualForExpect")
expect object MonitorDatabaseConstructor : RoomDatabaseConstructor<MonitorDatabase> {
    override fun initialize(): MonitorDatabase
}
