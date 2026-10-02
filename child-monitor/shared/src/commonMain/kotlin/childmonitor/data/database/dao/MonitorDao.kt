package childmonitor.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import childmonitor.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitorDao {
    @Insert suspend fun insertSession(value: SessionEntity)
    @Insert suspend fun insertMedia(value: MediaEntity)
    @Insert suspend fun insertRevision(value: ConfigRevisionEntity)
    @Insert suspend fun insertSettings(value: MonitorSettingsEntity)
    @Insert suspend fun insertEvaluation(value: EvaluationEntity)
    @Update suspend fun updateSession(value: SessionEntity)
    @Update suspend fun updateMedia(value: MediaEntity)
    @Upsert suspend fun putEvents(values: List<EventEntity>)
    @Upsert suspend fun putCoverage(values: List<CoverageEntity>)
    @Upsert suspend fun putRest(values: List<RestWindowEntity>)
    @Upsert suspend fun putReminder(value: ReminderEntity)
    @Upsert suspend fun putIntent(value: OperationIntentEntity)

    @Query("SELECT sessions.* FROM sessions JOIN media ON sessions.id = media.session_id WHERE sessions.state = 'Stopped' AND media.save_state = 'Saved' AND sessions.started_wall_us < :beforeUs ORDER BY sessions.started_wall_us, sessions.id")
    suspend fun expiredVideos(beforeUs: Long): List<SessionEntity>
    @Query("SELECT COUNT(*) FROM media WHERE save_state IN ('Finalizing', 'RetryableFailure')")
    suspend fun pendingMediaCount(): Int
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): SessionEntity?
    @Query("SELECT * FROM media WHERE session_id = :id") suspend fun media(id: String): MediaEntity?
    @Query("SELECT * FROM evaluations WHERE session_id = :id") suspend fun evaluation(id: String): EvaluationEntity?
    @Query("SELECT * FROM monitor_settings WHERE id = 1") suspend fun settings(): MonitorSettingsEntity?
    @Query("SELECT * FROM monitor_settings WHERE id = 1") fun settingsFlow(): Flow<MonitorSettingsEntity?>
    @Query("SELECT * FROM events WHERE session_id = :id ORDER BY start_us, id") suspend fun events(id: String): List<EventEntity>
    @Query("SELECT * FROM coverage WHERE session_id = :id ORDER BY start_us, id") suspend fun coverage(id: String): List<CoverageEntity>
    @Query("SELECT * FROM rest_windows WHERE session_id = :id ORDER BY start_us, id") suspend fun rest(id: String): List<RestWindowEntity>
    @Query("SELECT * FROM config_revisions WHERE session_id = :id ORDER BY effective_us, id") suspend fun revisions(id: String): List<ConfigRevisionEntity>
    @Query("SELECT * FROM operation_intents ORDER BY id") suspend fun intents(): List<OperationIntentEntity>
    @Query("SELECT * FROM sessions WHERE state != 'Stopped' OR id IN (SELECT session_id FROM media WHERE save_state IN ('Finalizing','RetryableFailure')) OR id NOT IN (SELECT session_id FROM evaluations)")
    suspend fun unfinished(): List<SessionEntity>
    @Query("SELECT * FROM sessions WHERE end_reason IS NOT NULL AND end_reason NOT IN ('UserStop', 'TargetReached') AND interruption_acknowledged = 0 ORDER BY started_wall_us DESC LIMIT 1")
    suspend fun interruption(): SessionEntity?
    @Query("""SELECT sessions.* FROM sessions LEFT JOIN media ON sessions.id = media.session_id
        WHERE (:beforeTime IS NULL OR started_wall_us < :beforeTime OR (started_wall_us = :beforeTime AND sessions.id < :beforeId))
        AND (:fromDate IS NULL OR local_date >= :fromDate) AND (:toDate IS NULL OR local_date <= :toDate)
        AND (:status = 'all' OR (:status = 'normal' AND end_reason IN ('UserStop','TargetReached'))
          OR (:status = 'interrupted' AND end_reason NOT IN ('UserStop','TargetReached'))
          OR (:status = 'pending' AND save_state IN ('Finalizing','RetryableFailure')) OR (:status = 'metadata' AND save_state = 'MetadataOnly'))
        AND (:text = '' OR instr(title, :text) > 0 OR instr(note, :text) > 0)
        ORDER BY started_wall_us DESC, sessions.id DESC LIMIT :limit""")
    suspend fun sessions(beforeTime: Long?, beforeId: String?, limit: Int, fromDate: String?, toDate: String?, status: String, text: String): List<SessionEntity>
    @Query("SELECT * FROM sessions WHERE state = 'Stopped' AND local_date BETWEEN :fromDate AND :toDate ORDER BY local_date, started_wall_us")
    suspend fun statisticsSessions(fromDate: String, toDate: String): List<SessionEntity>
    @Query("UPDATE sessions SET title = :title, note = :note WHERE id = :id AND state = 'Stopped'")
    suspend fun updateNote(id: String, title: String, note: String): Int
    @Query("SELECT id FROM sessions WHERE state = 'Stopped' AND (:protectedId IS NULL OR id != :protectedId)")
    suspend fun deletableIds(protectedId: String?): List<String>
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM operation_intents WHERE id = :id") suspend fun deleteIntent(id: String)
    @Query("UPDATE monitor_settings SET monitor_revision = monitor_revision + 1, settings_json = :json WHERE id = 1 AND monitor_revision = :expected")
    suspend fun updateSettings(json: String, expected: Long): Int
    @Query("UPDATE sessions SET interruption_acknowledged = 1 WHERE id = :id") suspend fun acknowledge(id: String)
    @Query("UPDATE sessions SET celebration_shown = 1 WHERE id = :id AND celebration_shown = 0") suspend fun markCelebrated(id: String): Int

    @Transaction
    suspend fun createSession(session: SessionEntity, revision: ConfigRevisionEntity, media: MediaEntity) {
        insertSession(session)
        insertRevision(revision)
        insertMedia(media)
    }

    @Transaction
    suspend fun checkpoint(session: SessionEntity, events: List<EventEntity>, coverage: List<CoverageEntity>, rest: List<RestWindowEntity>) {
        putEvents(events)
        putCoverage(coverage)
        putRest(rest)
        updateSession(session)
    }

    @Transaction
    suspend fun changeSettings(json: String, expected: Long, revision: ConfigRevisionEntity?,
        events: List<EventEntity>, coverage: List<CoverageEntity>, rest: List<RestWindowEntity>) {
        check(updateSettings(json, expected) == 1) { "Settings revision conflict" }
        putEvents(events)
        putCoverage(coverage)
        putRest(rest)
        if (revision != null) insertRevision(revision)
    }

    @Transaction
    suspend fun finishSave(media: MediaEntity, operationId: String) {
        updateMedia(media)
        deleteIntent(operationId)
    }
}
