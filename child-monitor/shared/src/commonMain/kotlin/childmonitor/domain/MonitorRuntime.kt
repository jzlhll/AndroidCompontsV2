package childmonitor.domain

import childmonitor.data.coordinator.RecordCoordinator
import childmonitor.data.database.entity.*
import childmonitor.data.repository.MonitorRepository
import childmonitor.data.repository.SettingsRepository
import childmonitor.model.*
import childmonitor.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** 唯一应用级会话控制器，串行提交事实和快照；已接受的任务不依赖页面存活。 */
@OptIn(kotlin.time.ExperimentalTime::class)
class MonitorRuntime(
    private val files: FileStorage,
    private val clock: RuntimeClock,
    private val settings: SettingsRepository,
    val records: MonitorRepository,
    private val capture: CaptureAdapter,
    private val audio: ReminderPlayer,
    private val playback: PlaybackRegistry,
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val dao = records.dao
    private val coordinator = RecordCoordinator(dao, files, records)
    private val mutableSnapshotFlow = MutableStateFlow(UiSnapshot())
    val snapshotFlow = mutableSnapshotFlow.asStateFlow()
    private var current: SessionEntity? = null
    private var engine: DetectionEngine? = null
    private var generation = 0L
    private var revisionId = ""
    private var originUs: Long? = null
    private var invalidSinceUs: Long? = null
    private var preparingSinceUs = 0L
    private var lastObservationUs = 0L
    private var recoveringSinceUs: Long? = null
    private var lastStorageCheckUs = 0L
    private var lastCheckpointUs = 0L
    private var lastInteractionUs = 0L
    private var startJob: Job? = null
    private var reminderJob: Job? = null
    private var terminalAudioJob: Job? = null
    private val reminders = ReminderScheduler()

    init {
        scope.launch { initialize(clock.newId()) }
        scope.launch {
            while (isActive) {
                delay(250.milliseconds)
                var timeout: EndReason? = null
                try {
                    mutex.withLock {
                        val state = snapshotFlow.value
                        if (state.runState !in listOf(RunState.Preparing, RunState.Monitoring)) return@withLock
                        val elapsed = elapsed()
                        val config = checkNotNull(state.settings).monitorSettings
                        if (elapsed - lastObservationUs > DefaultMonitorConfig.evidenceMaxGapMs * 1_000) {
                            engine?.unknown(elapsed, revisionId)
                            if (invalidSinceUs == null) invalidSinceUs = lastObservationUs
                        }
                        if (state.runState == RunState.Preparing && elapsed - preparingSinceUs >= DefaultMonitorConfig.ineffectiveTimeoutMs * 1_000 && engine?.calibration == null) timeout = EndReason.PrepareTimeout
                        if (timeout == null && invalidSinceUs?.let { elapsed - it >= DefaultMonitorConfig.ineffectiveTimeoutMs * 1_000 } == true) timeout = EndReason.IneffectiveTimeout
                        val needsAction = engine?.lastFeedback?.needsGuardian == true || invalidSinceUs?.let { elapsed - it >= DefaultMonitorConfig.qualityActionMs * 1_000 } == true
                        val darkened = state.runState == RunState.Monitoring && !needsAction &&
                            clock.monotonicUs() - lastInteractionUs >= checkNotNull(state.settings).preferences.darkenAfterMs * 1_000
                        publish { it.copy(durationUs = elapsed, needsGuardian = needsAction, darkened = darkened,
                            unclear = engine?.lastFeedback?.unclear ?: true) }
                        if (elapsed - lastCheckpointUs >= DefaultMonitorConfig.checkpointMs * 1_000) {
                            checkpoint(elapsed)
                        }
                        if (elapsed - lastStorageCheckUs >= 5_000_000L) {
                            lastStorageCheckUs = elapsed
                            if (files.availableBytes() < DefaultMonitorConfig.lowWaterBytes) timeout = EndReason.StorageLow
                        }
                        scheduleReminder(elapsed, config)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { timeout = EndReason.CaptureFailed }
                timeout?.let { stop(clock.newId(), it) }
            }
        }
    }

    suspend fun initialize(requestId: String) = command(requestId) {
        check(snapshotFlow.value.runState in listOf(RunState.Idle, RunState.Stopped))
        val config = settings.read()
        val recoveryFailures = coordinator.recover()
        records.invalidateRecords()
        val interruptionId = dao.interruption()?.id
        publish { it.copy(ready = true, settings = config, interruptionId = interruptionId, error = if (recoveryFailures == 0) null else ErrorCode.OperationFailed) }
        Unit
    }

    suspend fun refreshStorage(requestId: String): CommandResult<Long> = command(requestId) {
        publish { it.copy(storageStatus = StorageStatus.Checking) }
        val bytes = try { files.availableBytes() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { publish { it.copy(storageStatus = StorageStatus.Failed) }; throw CommandFailure(ErrorCode.StorageReadFailed) }
        publish { it.copy(storageStatus = StorageStatus.Available, availableBytes = bytes, checkedAtUs = clock.monotonicUs()) }
        bytes
    }

    suspend fun start(requestId: String) = command(requestId) {
        val state = snapshotFlow.value
        check(state.ready && state.captureReleased && !state.operationBusy && state.runState in listOf(RunState.Idle, RunState.Stopped))
        if (files.availableBytes() < DefaultMonitorConfig.estimatedSessionBytes) {
            publish { it.copy(error = ErrorCode.StorageLow) }
            throw CommandFailure(ErrorCode.StorageLow)
        }
        current?.let { previous ->
            if (dao.session(previous.id) != null && dao.evaluation(previous.id) == null) finalizeStoppedRecord(previous.id)
        }
        reminderJob?.cancel()
        terminalAudioJob?.cancel()
        generation++
        val token = generation
        val id = clock.newId()
        current = null; engine = null; preparingSinceUs = 0
        originUs = null; invalidSinceUs = null; recoveringSinceUs = null; lastStorageCheckUs = 0; lastObservationUs = 0; lastCheckpointUs = 0
        lastInteractionUs = clock.monotonicUs()
        reminders.clear()
        publish { it.copy(runState = RunState.Starting, captureReleased = false, sessionId = id,
            resultId = null, interruptionId = null, error = null, reminderId = null, emptySeatReady = false, needsGuardian = false, darkened = false) }
        startJob = scope.launch {
            try {
                val path = mutex.withLock {
                    val config = checkNotNull(snapshotFlow.value.settings)
                    val wall = clock.wallUs()
                    val zone = clock.timezoneId()
                    val date = Instant.fromEpochMilliseconds(wall / 1_000).toLocalDateTime(TimeZone.of(zone)).date.toString()
                    revisionId = clock.newId()
                    val session = SessionEntity(id, zone, date, wall, initialConfigId = revisionId)
                    val media = MediaEntity(id, "sessions/$date/$id/video.mp4", "sessions/$date/$id/recording.pending.mp4", configRevisionId = revisionId)
                    files.create(coordinator.manifest(session, media, "recording"))
                    dao.createSession(session, ConfigRevisionEntity(revisionId, id, 0, Json.encodeToString(config.monitorSettings)), media)
                    current = session
                    engine = DetectionEngine(id, clock::newId)
                    media.stagingPath
                }
                val first = withTimeout(DefaultMonitorConfig.startTimeoutMs.milliseconds) {
                    capture.start(id, token, path, { observation -> scope.launch {
                        try { accept(observation) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { stop(clock.newId(), EndReason.CaptureFailed, token) }
                    } },
                        { reason -> scope.launch { stop(clock.newId(), reason, token) } })
                }
                mutex.withLock {
                    if (generation == token && snapshotFlow.value.runState == RunState.Starting) {
                        originUs = first
                        current = checkNotNull(current).copy(state = RunState.Preparing.name)
                        dao.updateSession(checkNotNull(current))
                        publish { it.copy(runState = RunState.Preparing, durationUs = elapsed()) }
                    }
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) stop(clock.newId(), EndReason.CaptureFailed, token)
                else throw e
            } catch (_: Exception) { stop(clock.newId(), EndReason.CaptureFailed, token) }
        }
        id
    }

    suspend fun stop(requestId: String, reason: EndReason = EndReason.UserStop, expectedGeneration: Long? = null) = command(requestId) {
        if (expectedGeneration != null && expectedGeneration != generation) return@command Unit
        val state = snapshotFlow.value
        if (state.runState !in listOf(RunState.Starting, RunState.Preparing, RunState.Monitoring)) return@command Unit
        val end = elapsed()
        val stopGeneration = generation
        val starter = startJob
        val speaker = reminderJob
        publish { it.copy(runState = RunState.Stopping, durationUs = end, darkened = false) }
        reminderJob?.cancel(); reminders.clear()
        if (reason != EndReason.UserStop) engine?.interrupt(end, revisionId)
        engine?.close(end, if (reason == EndReason.UserStop) "session_end" else "interrupted")
        current = current?.copy(durationUs = end, endReason = reason.name, state = RunState.Stopping.name)
        if (reason != EndReason.UserStop && state.settings?.monitorSettings?.soundEnabled == true) {
            launchTerminalAudio(if (reason == EndReason.SystemLocked) "reminder_interruption_locked" else "reminder_interruption_generic", speaker)
        }
        scope.launch {
            var released = false
            try {
              withTimeout(DefaultMonitorConfig.stopSaveTimeoutMs.milliseconds) {
                // 不在启动协程自身 join 自身，停止任务独立拥有收尾过程。
                starter?.cancelAndJoin()
                speaker?.cancelAndJoin()
                val finish = capture.stop()
                released = finish.released
                mutex.withLock {
                    var session = current
                    if (session != null) {
                        val media = checkNotNull(dao.media(session.id))
                        val info = files.probe(media.stagingPath)
                        if (originUs == null && info == null && finish.released) {
                            session = session.copy(state = RunState.Stopped.name)
                            dao.updateSession(session)
                            coordinator.delete(session.id, null, requestId)
                            session = null
                            current = null
                        } else {
                            session = session.copy(state = RunState.Stopped.name,
                                durationUs = if (originUs == null) info?.durationUs ?: end else end)
                            current = session
                            checkpoint(session.durationUs)
                            records.evaluateOnce(session.id)
                            if (finish.released && finish.finalized) coordinator.save(session.id, requestId, generation)
                            else dao.updateMedia(media.copy(saveState = SaveState.RetryableFailure.name, recoverable = false, errorCode = "capture_not_finalized"))
                        }
                    }
                    if (reason == EndReason.UserStop && state.settings?.monitorSettings?.soundEnabled == true &&
                        session?.let { dao.media(it.id)?.saveState != SaveState.Saved.name } == true) launchTerminalAudio("reminder_save_failed", speaker)
                    records.invalidateRecords()
                    publish { it.copy(runState = RunState.Stopped, captureReleased = finish.released,
                        resultId = if (reason == EndReason.UserStop) session?.id else null,
                        interruptionId = if (reason != EndReason.UserStop) session?.id else null,
                        error = if (!finish.released || session == null && reason != EndReason.UserStop) ErrorCode.CaptureFailed else null) }
                }
              }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                failedStop(released, reason, stopGeneration)
            } catch (_: Exception) {
                failedStop(released, reason, stopGeneration)
            }
        }
        Unit
    }

    private fun launchTerminalAudio(clip: String, previous: Job?) {
        terminalAudioJob?.cancel()
        terminalAudioJob = scope.launch {
            try {
                previous?.cancelAndJoin()
                withTimeout(30_000.milliseconds) { audio.play(clip) {} }
            } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
            catch (_: Exception) { /* 系统提醒与停止保存独立，播放失败不影响收尾。 */ }
        }
    }

    private suspend fun failedStop(released: Boolean, reason: EndReason, token: Long) = mutex.withLock {
        if (generation != token) return@withLock
        current = current?.copy(state = RunState.Stopped.name)
        publish { it.copy(runState = RunState.Stopped, captureReleased = released,
            resultId = if (reason == EndReason.UserStop) current?.id else null,
            interruptionId = if (reason != EndReason.UserStop) current?.id else null,
            error = ErrorCode.OperationFailed, darkened = false) }
        try {
            withTimeout(2_000.milliseconds) {
                current?.let { session ->
                    // 保留真实的已提交检查点；事实事务成功前不能固定评价。
                    dao.updateSession(session)
                    finalizeStoppedRecord(session.id)
                    dao.media(session.id)?.let { media ->
                        if (media.saveState != SaveState.Saved.name) dao.updateMedia(media.copy(saveState = SaveState.Finalizing.name, errorCode = "stop_incomplete"))
                    }
                }
            }
        } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
        catch (_: Exception) { /* 已有检查点和未生成的评价交给重试或下次恢复。 */ }
        finally { records.invalidateRecords() }
    }

    private suspend fun finalizeStoppedRecord(id: String) {
        val session = current?.takeIf { it.id == id }
        val facts = engine?.facts()
        if (session != null && facts != null) {
            check(session.state == RunState.Stopped.name)
            val finalized = session.copy(lastCheckpointUs = session.durationUs)
            dao.checkpoint(finalized, facts.events, facts.coverage, facts.rest)
            current = finalized
            records.evaluateOnce(id)
        } else coordinator.finalizeMetadata(id)
    }

    private suspend fun accept(observation: Observation) = mutex.withLock {
        val state = snapshotFlow.value
        val first = originUs ?: return@withLock
        if (observation.sessionId != current?.id || observation.generation != generation || state.runState !in listOf(RunState.Preparing, RunState.Monitoring)) return@withLock
        val time = observation.frameTimeUs - first
        if (time < 0 || time <= lastObservationUs || elapsed() - time > DefaultMonitorConfig.evidenceMaxGapMs * 1_000) return@withLock
        lastObservationUs = time
        val engine = checkNotNull(engine)
        val previousCalibration = engine.calibration
        val previousFacts = engine.facts()
        val previousFeedback = engine.lastFeedback
        val feedback = engine.accept(observation.copy(frameTimeUs = time), checkNotNull(state.settings).monitorSettings, revisionId)
        if (feedback.unclear) {
            recoveringSinceUs = null
            if (invalidSinceUs == null) invalidSinceUs = time
        } else if (invalidSinceUs != null) {
            if (recoveringSinceUs == null) recoveringSinceUs = time
            if (time - checkNotNull(recoveringSinceUs) >= DefaultMonitorConfig.recoverConfirmMs * 1_000) invalidSinceUs = null
        }
        if (feedback.calibration != previousCalibration && feedback.calibration != null) {
            revisionId = clock.newId()
            dao.insertRevision(ConfigRevisionEntity(revisionId, observation.sessionId, time,
                Json.encodeToString(checkNotNull(state.settings).monitorSettings), Json.encodeToString(feedback.calibration)))
            current = checkNotNull(current).copy(state = RunState.Monitoring.name)
        }
        reminders.playing?.let { if (!reminders.stillValid(it, feedback)) reminderJob?.cancel() }
        publish { it.copy(runState = if (feedback.calibration != null) RunState.Monitoring else RunState.Preparing,
            seated = feedback.seated, emptySeatReady = engine.emptySeatReady, away = feedback.away, unclear = feedback.unclear, needsGuardian = feedback.needsGuardian) }
        val nextFacts = engine.facts()
        if (nextFacts.events.size != previousFacts.events.size || nextFacts.coverage.size != previousFacts.coverage.size ||
            previousFeedback.activeKinds != feedback.activeKinds || feedback.calibration != previousCalibration) checkpoint(time)
    }

    suspend fun confirmPlacement(region: SeatRegion, requestId: String) = command(requestId) {
        check(snapshotFlow.value.runState == RunState.Preparing)
        checkNotNull(engine).confirmRegion(region)
        publish { it.copy(emptySeatReady = false) }
    }
    suspend fun reposition(requestId: String) = command(requestId) {
        check(snapshotFlow.value.runState in listOf(RunState.Preparing, RunState.Monitoring))
        preparingSinceUs = elapsed()
        engine?.reposition(preparingSinceUs)
        recoveringSinceUs = null
        publish { it.copy(runState = RunState.Preparing, emptySeatReady = false, seated = false, away = false,
            unclear = true, needsGuardian = false, darkened = false) }
    }
    suspend fun interact(requestId: String) = command(requestId) {
        lastInteractionUs = clock.monotonicUs()
        publish { it.copy(darkened = false) }
    }
    suspend fun updateSettings(value: MonitorSettings, expectedRevision: Long, requestId: String) = command(requestId) {
        value.validate()
        check(snapshotFlow.value.runState !in listOf(RunState.Starting, RunState.Stopping))
        val old = checkNotNull(snapshotFlow.value.settings)
        if (old.monitorRevision != expectedRevision) throw CommandFailure(ErrorCode.SettingsConflict)
        val time = elapsed()
        val nextId = clock.newId()
        val active = snapshotFlow.value.runState in listOf(RunState.Preparing, RunState.Monitoring)
        val facts = if (active) engine?.factsAtConfigBoundary() else null
        val revision = current?.takeIf { snapshotFlow.value.runState in listOf(RunState.Preparing, RunState.Monitoring) }?.let {
            ConfigRevisionEntity(nextId, it.id, time, Json.encodeToString(value), engine?.calibration?.let { Json.encodeToString(it) })
        }
        dao.changeSettings(Json.encodeToString(value), expectedRevision, revision,
            facts?.events.orEmpty(), facts?.coverage.orEmpty(), facts?.rest.orEmpty())
        if (revision != null) { engine?.changeConfig(time); revisionId = nextId }
        if (old.monitorSettings.soundEnabled && !value.soundEnabled) reminderJob?.cancel()
        publish { it.copy(settings = old.copy(monitorRevision = expectedRevision + 1, monitorSettings = value)) }
    }
    suspend fun updatePreferences(patch: UserPreferencesPatch, expectedRevision: Long, requestId: String) = command(requestId) {
        checkIdle()
        val saved = settings.preferences.update(patch, expectedRevision)
        publish { it.copy(settings = checkNotNull(it.settings).copy(preferences = saved)) }
    }
    suspend fun retrySave(id: String, requestId: String) = command(requestId) {
        checkIdle()
        publish { it.copy(operationBusy = true) }
        try { withTimeout(DefaultMonitorConfig.stopSaveTimeoutMs.milliseconds) {
            finalizeStoppedRecord(id)
            coordinator.save(id, requestId, generation)
        } }
        finally { records.invalidateRecords(); publish { it.copy(operationBusy = false) } }
    }
    suspend fun keepStatistics(id: String, requestId: String) = command(requestId) {
        checkIdle()
        val media = checkNotNull(dao.media(id))
        check(media.saveState == SaveState.Unrecoverable.name)
        dao.updateMedia(media.copy(saveState = SaveState.MetadataOnly.name))
        records.invalidateRecords()
    }
    suspend fun deleteSessions(ids: List<String>, protectedId: String?, requestId: String) = command(requestId) {
        checkIdle()
        val failed = mutableListOf<String>()
        for (id in ids.distinct()) {
            try { require(id != protectedId); playback.releaseSession(id); coordinator.delete(id, protectedId, requestId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed += id }
        }
        records.invalidateRecords()
        publish { it.copy(resultId = it.resultId?.takeIf { id -> id !in ids || id in failed },
            interruptionId = it.interruptionId?.takeIf { id -> id !in ids || id in failed }) }
        failed
    }
    suspend fun acknowledge(id: String, requestId: String) = command(requestId) {
        dao.acknowledge(id)
        publish { it.copy(interruptionId = null) }
    }
    suspend fun claimCelebration(id: String, requestId: String) = command(requestId) {
        val session = dao.session(id)
        val result = records.readResult(id)
        session?.endReason == EndReason.UserStop.name && session.durationUs > DefaultMonitorConfig.evaluationMinUs &&
            result?.media?.saveState == SaveState.Saved.name && dao.markCelebrated(id) == 1
    }
    suspend fun consumeResult(requestId: String) = command(requestId) { publish { it.copy(resultId = null) } }

    private suspend fun checkpoint(time: Long) {
        val session = current ?: return
        val facts = engine?.facts() ?: return
        val updated = session.copy(durationUs = time, lastCheckpointUs = time)
        dao.checkpoint(updated, facts.events, facts.coverage, facts.rest)
        current = updated
        dao.media(session.id)?.let { files.writeManifest(coordinator.manifest(updated, it, if (updated.state == RunState.Stopped.name) "finalizing" else "recording")) }
        lastCheckpointUs = time
    }
    private suspend fun scheduleReminder(time: Long, config: MonitorSettings) {
        val engine = engine ?: return
        val kind = reminders.next(time, engine.lastFeedback, config, invalidSinceUs, engine.ordinaryAwayUs(), engine.allowedAwayUs(config), engine.seatedSinceUs) ?: return
        val id = clock.newId()
        val sessionId = checkNotNull(current).id
        val token = generation
        val reminderOriginUs = originUs
        fun reminderElapsed() = reminderOriginUs?.let { clock.monotonicUs() - it } ?: 0L
        var completed = false
        val eventKind = when (kind) {
            "reminder_head_down" -> EventKind.HeadDown
            "reminder_head_tilt" -> EventKind.HeadTilt
            "reminder_body_lean" -> EventKind.BodyLean
            "reminder_away" -> EventKind.Away
            "reminder_unclear" -> EventKind.Uncertain
            else -> null
        }
        val eventId = engine.facts().events.lastOrNull { it.kind == eventKind?.name && it.endReason == "active" }?.id
        var row = ReminderEntity(id, sessionId, eventId = eventId, kind = kind, requestedUs = time, result = "queued")
        dao.putReminder(row)
        publish { it.copy(reminderId = kind) }
        reminderJob = scope.launch {
            var persistenceFailed = false
            try {
                val success = if (!config.soundEnabled) {
                    val started = mutex.withLock {
                        row = row.copy(startedUs = elapsed(), result = "started")
                        if (persistReminder(row)) {
                            reminders.started(kind, elapsed(), engine.seatedSinceUs)
                            if (kind == "reminder_rest") engine.allowRest(elapsed())
                            true
                        } else { persistenceFailed = true; false }
                    }
                    if (started) delay(3_000.milliseconds)
                    started
                } else withTimeout(30_000.milliseconds) {
                    audio.play(kind) {
                        val startedUs = clock.monotonicUs()
                        scope.launch {
                            mutex.withLock {
                                if (completed || generation != token || snapshotFlow.value.runState !in listOf(RunState.Preparing, RunState.Monitoring) ||
                                    !reminders.stillValid(kind, engine.lastFeedback)) return@withLock
                                val now = startedUs - (reminderOriginUs ?: startedUs)
                                row = row.copy(startedUs = now, result = "started")
                                if (persistReminder(row)) {
                                    reminders.started(kind, now, engine.seatedSinceUs)
                                    if (kind == "reminder_rest") engine.allowRest(now)
                                } else {
                                    persistenceFailed = true
                                    scope.launch { stop(clock.newId(), EndReason.CaptureFailed, token) }
                                }
                            }
                        }
                    }
                }
                mutex.withLock {
                    completed = true
                    if (!persistReminder(row.copy(finishedUs = reminderElapsed(), result = if (success) "completed" else "failed"))) persistenceFailed = true
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val persisted = withTimeoutOrNull(1_000.milliseconds) {
                        mutex.withLock {
                            completed = true
                            persistReminder(row.copy(finishedUs = reminderElapsed(), result = "canceled"))
                        }
                    }
                    if (persisted != true) persistenceFailed = true
                }
                throw e
            } catch (_: Exception) {
                mutex.withLock {
                    completed = true
                    if (!persistReminder(row.copy(finishedUs = reminderElapsed(), result = "failed"))) persistenceFailed = true
                }
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock {
                        completed = true
                        if (generation == token) {
                            reminders.finished(elapsed())
                            publish { it.copy(reminderId = null) }
                        }
                    }
                }
                if (persistenceFailed) scope.launch { stop(clock.newId(), EndReason.CaptureFailed, token) }
            }
        }
    }

    /** 调用方已持有状态锁，写入失败只报告状态，不在错误分支再次抛数据库异常。 */
    private suspend fun persistReminder(row: ReminderEntity): Boolean = try {
        dao.putReminder(row)
        true
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { publish { it.copy(error = ErrorCode.OperationFailed) }; false }

    private fun checkIdle() { check(snapshotFlow.value.captureReleased && snapshotFlow.value.runState in listOf(RunState.Idle, RunState.Stopped)) }
    private fun elapsed(): Long = originUs?.let { clock.monotonicUs() - it } ?: 0
    private fun publish(change: (UiSnapshot) -> UiSnapshot) {
        mutableSnapshotFlow.value = change(snapshotFlow.value).copy(revision = snapshotFlow.value.revision + 1)
    }
    private suspend fun <T> command(requestId: String, block: suspend () -> T): CommandResult<T> = scope.async {
        mutex.withLock {
            try { CommandResult.Success(requestId, block()) }
            catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                publish { it.copy(error = ErrorCode.OperationFailed) }
                CommandResult.Failure(requestId, ErrorCode.OperationFailed, true)
            }
            catch (e: CommandFailure) { publish { it.copy(error = e.code) }; CommandResult.Failure(requestId, e.code, true) }
            catch (_: Exception) { publish { it.copy(error = ErrorCode.OperationFailed) }; CommandResult.Failure(requestId, ErrorCode.OperationFailed, true) }
        }
    }.await()
    private class CommandFailure(val code: ErrorCode) : Exception()
    fun close() { scope.cancel() }
}
