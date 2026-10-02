package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions", indices = [Index(value = ["started_wall_us", "id"])])
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "timezone_id") val timezoneId: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "started_wall_us") val startedWallUs: Long,
    @ColumnInfo(name = "duration_us") val durationUs: Long = 0,
    @ColumnInfo(name = "state") val state: String = "Starting",
    @ColumnInfo(name = "end_reason") val endReason: String? = null,
    @ColumnInfo(name = "interruption_acknowledged") val interruptionAcknowledged: Boolean = false,
    @ColumnInfo(name = "celebration_shown") val celebrationShown: Boolean = false,
    @ColumnInfo(name = "initial_config_id") val initialConfigId: String,
    @ColumnInfo(name = "last_checkpoint_us") val lastCheckpointUs: Long = 0,
)
