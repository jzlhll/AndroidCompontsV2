package childmonitor.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "operation_intents", foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["session_id"])])
data class OperationIntentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "stage") val stage: String,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
)
