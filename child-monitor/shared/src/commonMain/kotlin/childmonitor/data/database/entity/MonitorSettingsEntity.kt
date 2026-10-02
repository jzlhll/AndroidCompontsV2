package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "monitor_settings")
data class MonitorSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = 1,
    @ColumnInfo(name = "monitor_revision") val monitorRevision: Long,
    @ColumnInfo(name = "settings_json") val settingsJson: String,
)
