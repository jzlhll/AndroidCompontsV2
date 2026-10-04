package childmonitor.data.repository

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import childmonitor.data.database.dao.MonitorDao
import childmonitor.data.database.entity.*
import childmonitor.domain.EvaluationEngine
import childmonitor.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 页面读取记录的唯一入口，历史结果来自已保存评价快照。 */
class MonitorRepository(val dao: MonitorDao, private val access: () -> Boolean = { true }) {
    fun requireAccess() {
        val authorized = access()
        if (!authorized) logdNoFile(tag = TAG) { "record access denied" }
        check(authorized) { "Parent authorization required" }
    }
    private val mutableRevisionFlow = MutableStateFlow(0L)
    val revisionFlow = mutableRevisionFlow.asStateFlow()
    fun invalidateRecords() { mutableRevisionFlow.update { it + 1 } }
    data class Result(val session: SessionEntity, val media: MediaEntity?, val evaluation: EvaluationSnapshot?, val events: List<EventEntity>, val rest: List<RestWindowEntity>, val annotations: List<EventAnnotationEntity>)
    data class Summary(val session: SessionEntity, val media: MediaEntity?)
    suspend fun latestSavedVideo(): MediaEntity? {
        requireAccess()
        return dao.latestSavedVideo()
    }
    suspend fun readResult(id: String): Result? {
        requireAccess()
        val session = dao.session(id) ?: return null
        return Result(session, dao.media(id), dao.evaluation(id)?.let { Json.decodeFromString(it.snapshotJson) }, dao.events(id), dao.rest(id), dao.annotations(id))
    }
    suspend fun querySessions(cursor: SessionCursor?, limit: Int = 30, filter: RecordFilter = RecordFilter()): SessionPage<Summary> {
        requireAccess()
        require(limit in 1..100)
        filter.validate()
        val rows = dao.sessions(cursor?.startedWallUs, cursor?.id, limit + 1, filter.fromDate, filter.toDate, filter.status, filter.query)
        val visible = rows.take(limit)
        return SessionPage(visible.map { Summary(it, dao.media(it.id)) },
            if (rows.size > limit) visible.last().let { SessionCursor(it.startedWallUs, it.id) } else null)
    }
    suspend fun dailyStatistics(from: String, to: String): List<DailyStatistics> {
        requireAccess()
        RecordFilter(from, to).validate()
        val sessions = dao.statisticsSessions(from, to)
        return sessions.groupBy { it.localDate }.map { (date, group) ->
            val evaluations = group.mapNotNull { session -> dao.evaluation(session.id)?.let { Json.decodeFromString<EvaluationSnapshot>(it.snapshotJson) } }
            val items = listOf(EventKind.HeadDown, EventKind.HeadTilt, EventKind.BodyLean).map { kind ->
                val rows = evaluations.flatMap { it.items }.filter { it.kind == kind }
                ItemStatistics(kind, rows.sumOf { it.count }, rows.sumOf { it.abnormalUs }, rows.sumOf { it.validUs })
            }
            DailyStatistics(date, group.size, group.sumOf { it.durationUs }, evaluations.sumOf { it.seatedUs + it.awayUs + it.restUs },
                evaluations.sumOf { it.seatedUs }, evaluations.sumOf { it.awayCount }, items)
        }
    }
    suspend fun queryDeletableIds(protectedId: String?, filter: RecordFilter, selected: Set<String>? = null): Set<String> {
        requireAccess()
        filter.validate()
        if (selected == null) return dao.deletableIds(protectedId, filter.fromDate, filter.toDate, filter.status, filter.query).toSet()
        // 为筛选参数预留 SQLite 绑定位置，大量全选后的刷新也只读取 ID。
        return selected.chunked(900).flatMap { ids ->
            dao.deletableSelection(ids, protectedId, filter.fromDate, filter.toDate, filter.status, filter.query)
        }.toSet()
    }
    suspend fun evaluateOnce(id: String) {
        if (dao.evaluation(id) != null) {
            logdNoFile(tag = TAG) { "evaluate once reused sessionId=$id" }
            return
        }
        val session = checkNotNull(dao.session(id))
        val result = EvaluationEngine.evaluate(session, dao.events(id), dao.coverage(id), dao.rest(id), dao.revisions(id))
        dao.insertEvaluation(EvaluationEntity(id, result.version, Json.encodeToString(result)))
        logdNoFile(tag = TAG) { "evaluation committed sessionId=$id version=${result.version} grade=${result.grade} completionOnly=${result.completionOnly} partialData=${result.partialData} durationUs=${result.durationUs} seatedUs=${result.seatedUs} awayUs=${result.awayUs} restUs=${result.restUs} prepareUs=${result.prepareUs} unknownUs=${result.unknownUs} awayCount=${result.awayCount} timeoutAwayCount=${result.timeoutAwayCount}" }
    }
}
