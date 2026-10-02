package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reminders", foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["session_id"])])
data class ReminderEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "event_id") val eventId: String? = null,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "requested_us") val requestedUs: Long,
    @ColumnInfo(name = "started_us") val startedUs: Long? = null,
    @ColumnInfo(name = "finished_us") val finishedUs: Long? = null,
    @ColumnInfo(name = "result") val result: String,
)
