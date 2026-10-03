package childmonitor.data.coordinator

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
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
        logdNoFile(tag = TAG) { "save requested sessionId=$id requestId=$requestId generation=$generation state=${media.saveState}" }
        finalizeMetadata(id)
        if (media.saveState == SaveState.Saved.name) return@withLock media
        val session = checkNotNull(dao.session(id))
        check(session.state == RunState.Stopped.name)
        val intent = OperationIntentEntity("save:$id", id, "save", "probe", generation, requestId)
        check(dao.intents().none { it.sessionId == id && it.kind == "delete" })
        dao.putIntent(intent)
        logdNoFile(tag = TAG) { "save intent committed sessionId=$id requestId=$requestId generation=$generation stage=probe" }
        var available: MediaInfo? = null
        try {
            available = files.probe(media.relativePath) ?: files.probe(media.stagingPath)
            if (available == null) {
                val missing = media.copy(saveState = SaveState.Unrecoverable.name, recoverable = false, errorCode = "media_unavailable")
                dao.finishSave(missing, intent.id)
                loge(tag = TAG) { "save unavailable sessionId=$id requestId=$requestId state=${missing.saveState}" }
                return@withLock missing
            }
            if (files.probe(media.relativePath) == null) {
                // 同卷重命名，不为保存预留第二份完整视频空间。
                files.move(media.stagingPath, media.relativePath)
                logdNoFile(tag = TAG) { "save file moved sessionId=$id requestId=$requestId" }
            }
            val saved = media.copy(durationUs = available.durationUs, width = available.width, height = available.height,
                saveState = SaveState.Saved.name, recoverable = false, errorCode = null)
            files.writeManifest(manifest(session, saved, "saved"))
            dao.finishSave(saved, intent.id)
            logdNoFile(tag = TAG) { "save committed sessionId=$id requestId=$requestId generation=$generation state=${saved.saveState} durationUs=${saved.durationUs} width=${saved.width} height=${saved.height} videoOriginOffsetUs=${saved.videoOriginOffsetUs}" }
            saved
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            loge(tag = TAG) { "save failed sessionId=$id requestId=$requestId generation=$generation sourceVerified=${available != null} errorType=${e.javaClass.simpleName}" }
            val failure = media.copy(saveState = SaveState.RetryableFailure.name, recoverable = available != null, errorCode = "save_failed")
            dao.updateMedia(failure)
            dao.putIntent(intent.copy(stage = "failed", errorCode = "save_failed"))
            failure
        }
    }

    suspend fun purgeVideo(id: String, requestId: String) = mutex.withLock {
        val session = checkNotNull(dao.session(id))
        val media = checkNotNull(dao.media(id))
        check(session.state == RunState.Stopped.name && media.saveState in listOf(SaveState.Saved.name, SaveState.MetadataOnly.name))
        check(dao.intents().none { it.sessionId == id && it.kind == "delete" })
        val intent = OperationIntentEntity("purge:$id", id, "purge_video", "files", 0, requestId)
        dao.putIntent(intent)
        logdNoFile(tag = TAG) { "purge video intent committed sessionId=$id requestId=$requestId" }
        files.deleteVideo(media.relativePath, media.stagingPath, id)
        val updated = media.copy(saveState = SaveState.MetadataOnly.name, recoverable = false, errorCode = null, thumbnailPath = null)
        files.writeManifest(manifest(session, updated, "metadata_only"))
        dao.finishSave(updated, intent.id)
        logdNoFile(tag = TAG) { "purge video committed sessionId=$id requestId=$requestId state=${updated.saveState}" }
    }

    suspend fun delete(id: String, protectedId: String?, requestId: String) = mutex.withLock {
        require(id != protectedId)
        val session = dao.session(id) ?: return@withLock
        check(session.state == RunState.Stopped.name)
        val media = dao.media(id)
        val relativeDir = media?.relativePath?.substringBeforeLast('/') ?: "sessions/${session.localDate}/$id"
        val intent = OperationIntentEntity("delete:$id", id, "delete", "files", 0, requestId)
        dao.putIntent(intent)
        logdNoFile(tag = TAG) { "delete intent committed sessionId=$id requestId=$requestId" }
        files.deleteSession(relativeDir, id)
        dao.deleteSession(id)
        logdNoFile(tag = TAG) { "delete committed sessionId=$id requestId=$requestId" }
    }

    suspend fun recover(): Int {
        var failures = 0
        logdNoFile(tag = TAG) { "recover started" }
        // 删除优先；单条目录故障不阻止其他会话与设置恢复。
        val deleting = dao.intents().filter { it.kind == "delete" }
        for (intent in deleting) {
            try { delete(intent.sessionId, null, intent.requestId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { loge(tag = TAG) { "recover delete failed sessionId=${intent.sessionId} requestId=${intent.requestId} errorType=${e.javaClass.simpleName}" }; failures++ }
        }
        for (intent in dao.intents().filter { it.kind == "purge_video" }) {
            try { purgeVideo(intent.sessionId, intent.requestId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { loge(tag = TAG) { "recover purge failed sessionId=${intent.sessionId} requestId=${intent.requestId} errorType=${e.javaClass.simpleName}" }; failures++ }
        }
        val pendingDeletes = dao.intents().filter { it.kind in listOf("delete", "purge_video") }.map { it.sessionId }.toSet()
        for (session in dao.unfinished().filter { it.id !in pendingDeletes }) {
            try {
                val media = dao.media(session.id) ?: continue
                val info = files.probe(media.relativePath) ?: files.probe(media.stagingPath)
                val mediaEnd = info?.let { it.durationUs + media.videoOriginOffsetUs } ?: session.lastCheckpointUs
                val end = if (mediaEnd > session.lastCheckpointUs) mediaEnd else session.lastCheckpointUs
                val interrupted = session.state != RunState.Stopped.name
                logdNoFile(tag = TAG) { "recover session sessionId=${session.id} state=${session.state} checkpointUs=${session.lastCheckpointUs} storedDurationUs=${session.durationUs} mediaEndUs=$mediaEnd recoveredEndUs=$end mediaVerified=${info != null}" }
                val recovered = session.copy(state = RunState.Stopped.name,
                    durationUs = if (interrupted) end else session.durationUs,
                    endReason = session.endReason ?: EndReason.CaptureFailed.name)
                if (dao.evaluation(session.id) == null) {
                    restoreFacts(recovered)
                    records.evaluateOnce(session.id)
                }
                if (save(session.id, "recovery:${session.id}", 0).saveState == SaveState.RetryableFailure.name) failures++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { loge(tag = TAG) { "recover session failed sessionId=${session.id} errorType=${e.javaClass.simpleName}" }; failures++ }
        }
        // 无数据库记录的清单不自动认领成完整会话，保留原目录供故障排查。
        files.manifests()
        logdNoFile(tag = TAG) { "recover finished failures=$failures" }
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
        logdNoFile(tag = TAG) { "restore facts committed sessionId=${session.id} previousCheckpointUs=${session.lastCheckpointUs} durationUs=${session.durationUs} unknownTail=${session.durationUs > session.lastCheckpointUs}" }
    }

    fun manifest(session: SessionEntity, media: MediaEntity, stage: String) = SessionManifest(
        1, session.id, media.relativePath.substringBeforeLast('/'), session.timezoneId, session.startedWallUs,
        media.videoOriginOffsetUs, stage, "recording.pending.mp4", "video.mp4", session.lastCheckpointUs,
        session.startedWallUs + session.lastCheckpointUs,
    )
}
