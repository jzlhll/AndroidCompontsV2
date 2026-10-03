package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 家长判断独立于算法事实，删除事件或会话时同步清理。 */
@Entity(tableName = "event_annotations", foreignKeys = [
    ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["session_id"])])
data class EventAnnotationEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "updated_wall_us") val updatedWallUs: Long,
)
