package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "coverage", foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ConfigRevisionEntity::class, parentColumns = ["id"], childColumns = ["config_revision_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["session_id"]), Index(value = ["config_revision_id"]), Index(value = ["session_id", "channel", "start_us"], unique = true)])
data class CoverageEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "channel") val channel: String,
    @ColumnInfo(name = "start_us") val startUs: Long,
    @ColumnInfo(name = "end_us") val endUs: Long,
    @ColumnInfo(name = "quality_reason") val qualityReason: String,
    @ColumnInfo(name = "config_revision_id") val configRevisionId: String,
)
