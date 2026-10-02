package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "config_revisions", foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["session_id"])])
data class ConfigRevisionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "effective_us") val effectiveUs: Long,
    @ColumnInfo(name = "settings_json") val settingsJson: String,
    @ColumnInfo(name = "calibration_json") val calibrationJson: String? = null,
    @ColumnInfo(name = "model_id") val modelId: String = "mlkit-pose-beta5/efficientdet-lite0-int8-v1",
    @ColumnInfo(name = "mapping_version") val mappingVersion: Int = 1,
)
