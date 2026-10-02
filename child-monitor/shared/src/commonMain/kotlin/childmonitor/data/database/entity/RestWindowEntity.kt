package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rest_windows", foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["session_id"]), Index(value = ["event_id"])])
data class RestWindowEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "start_us") val startUs: Long,
    @ColumnInfo(name = "end_us") val endUs: Long,
    @ColumnInfo(name = "kind") val kind: String = "rest_suggested",
)
