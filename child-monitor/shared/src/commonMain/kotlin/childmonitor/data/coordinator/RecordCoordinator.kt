package childmonitor.data.coordinator

import childmonitor.data.database.dao.MonitorDao
import childmonitor.data.database.entity.*
import childmonitor.data.repository.MonitorRepository
import childmonitor.model.*
import childmonitor.platform.FileStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 以持久化意图协调数据库与文件；所有调用由 Runtime 的应用级操作许可串行保护。 */
class RecordCoordinator(private val dao: MonitorDao, private val files: FileStorage, private val records: MonitorRepository) {
    private val mutex = Mutex()

    suspend fun save(id: String, requestId: String, generation: Long): MediaEntity = mutex.withLock {
        val media = checkNotNull(dao.media(id))
        finalizeMetadata(id)
        if (media.saveState == SaveState.Saved.name) return@withLock media
        val session = checkNotNull(dao.session(id))
        check(session.state == RunState.Stopped.name)
        val intent = OperationIntentEntity("save:$id", id, "save", "probe", generation, requestId)
        check(dao.intents().none { it.sessionId == id && it.kind == "delete" })
        dao.putIntent(intent)
        var available: MediaInfo? = null
        try {
            available = files.probe(media.relativePath) ?: files.probe(media.stagingPath)
            if (available == null) {
                val missing = media.copy(saveState = SaveState.Unrecoverable.name, recoverable = false, errorCode = "media_unavailable")
                dao.finishSave(missing, intent.id)
                return@withLock missing
            }
            if (files.probe(media.relativePath) == null) {
                // 同卷重命名，不为保存预留第二份完整视频空间。
                files.move(media.stagingPath, media.relativePath)
            }
            val saved = media.copy(durationUs = available.durationUs, width = available.width, height = available.height,
                saveState = SaveState.Saved.name, recoverable = false, errorCode = null)
            files.writeManifest(manifest(session, saved, "saved"))
            dao.finishSave(saved, intent.id)
            saved
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val failure = media.copy(saveState = SaveState.RetryableFailure.name, recoverable = available != null, errorCode = "save_failed")
            dao.updateMedia(failure)
            dao.putIntent(intent.copy(stage = "failed", errorCode = "save_failed"))
            failure
        }
    }

    suspend fun delete(id: String, protectedId: String?, requestId: String) = mutex.withLock {
        require(id != protectedId)
        val session = dao.session(id) ?: return@withLock
        check(session.state == RunState.Stopped.name)
        val media = dao.media(id)
        val relativeDir = media?.relativePath?.substringBeforeLast('/') ?: "sessions/${session.localDate}/$id"
        val intent = OperationIntentEntity("delete:$id", id, "delete", "files", 0, requestId)
        dao.putIntent(intent)
        files.deleteSession(relativeDir, id)
        dao.deleteSession(id)
    }

    suspend fun recover(): Int {
        var failures = 0
        // 删除优先；单条目录故障不阻止其他会话与设置恢复。
        val deleting = dao.intents().filter { it.kind == "delete" }
        for (intent in deleting) {
            try { delete(intent.sessionId, null, intent.requestId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures++ }
        }
        val pendingDeletes = dao.intents().filter { it.kind == "delete" }.map { it.sessionId }.toSet()
        for (session in dao.unfinished().filter { it.id !in pendingDeletes }) {
            try {
                val media = dao.media(session.id) ?: continue
                val info = files.probe(media.relativePath) ?: files.probe(media.stagingPath)
                val mediaEnd = info?.let { it.durationUs + media.videoOriginOffsetUs } ?: session.lastCheckpointUs
                val end = if (mediaEnd > session.lastCheckpointUs) mediaEnd else session.lastCheckpointUs
                val interrupted = session.state != RunState.Stopped.name
                val recovered = session.copy(state = RunState.Stopped.name,
                    durationUs = if (interrupted) end else session.durationUs,
                    endReason = session.endReason ?: EndReason.CaptureFailed.name)
                if (dao.evaluation(session.id) == null) {
                    restoreFacts(recovered)
                    records.evaluateOnce(session.id)
                }
                if (save(session.id, "recovery:${session.id}", 0).saveState == SaveState.RetryableFailure.name) failures++
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures++ }
        }
        // 无数据库记录的清单不自动认领成完整会话，保留原目录供故障排查。
        files.manifests()
        return failures
    }

    suspend fun finalizeMetadata(id: String) {
        val session = checkNotNull(dao.session(id))
        check(session.state == RunState.Stopped.name)
        if (dao.evaluation(id) != null) return
        restoreFacts(session)
        records.evaluateOnce(id)
    }

    private suspend fun restoreFacts(session: SessionEntity) {
        val events = dao.events(session.id).map { if (it.endReason == "active") it.copy(endReason = "interrupted") else it }
        val coverage = dao.coverage(session.id).toMutableList()
        if (session.durationUs > session.lastCheckpointUs) {
            val revision = dao.revisions(session.id).lastOrNull()?.id ?: session.initialConfigId
            val existing = coverage.indexOfFirst { it.channel == "unknown" && it.startUs == session.lastCheckpointUs }
            if (existing >= 0) coverage[existing] = coverage[existing].copy(endUs = session.durationUs)
            else coverage += CoverageEntity("recovery:${session.id}:${session.lastCheckpointUs}", session.id, "unknown",
                session.lastCheckpointUs, session.durationUs, "low_conf", revision)
        }
        dao.checkpoint(session.copy(lastCheckpointUs = session.durationUs), events, coverage, dao.rest(session.id))
    }

    fun manifest(session: SessionEntity, media: MediaEntity, stage: String) = SessionManifest(
        1, session.id, media.relativePath.substringBeforeLast('/'), session.timezoneId, session.startedWallUs,
        media.videoOriginOffsetUs, stage, "recording.pending.mp4", "video.mp4", session.lastCheckpointUs,
        session.startedWallUs + session.lastCheckpointUs,
    )
}
