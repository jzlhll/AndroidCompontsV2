package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "media", foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ConfigRevisionEntity::class, parentColumns = ["id"], childColumns = ["config_revision_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["config_revision_id"])])
data class MediaEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "staging_path") val stagingPath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "duration_us") val durationUs: Long = 0,
    @ColumnInfo(name = "width") val width: Int = 0,
    @ColumnInfo(name = "height") val height: Int = 0,
    @ColumnInfo(name = "video_origin_offset_us") val videoOriginOffsetUs: Long = 0,
    @ColumnInfo(name = "save_state") val saveState: String = "Finalizing",
    @ColumnInfo(name = "recoverable") val recoverable: Boolean = false,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "config_revision_id") val configRevisionId: String,
)
