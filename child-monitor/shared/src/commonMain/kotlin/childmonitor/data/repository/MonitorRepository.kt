package childmonitor.data.repository

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
class MonitorRepository(val dao: MonitorDao) {
    private val mutableRevisionFlow = MutableStateFlow(0L)
    val revisionFlow = mutableRevisionFlow.asStateFlow()
    fun invalidateRecords() { mutableRevisionFlow.update { it + 1 } }
    data class Result(val session: SessionEntity, val media: MediaEntity?, val evaluation: EvaluationSnapshot?, val events: List<EventEntity>, val rest: List<RestWindowEntity>)
    data class Summary(val session: SessionEntity, val media: MediaEntity?)
    suspend fun readResult(id: String): Result? {
        val session = dao.session(id) ?: return null
        return Result(session, dao.media(id), dao.evaluation(id)?.let { Json.decodeFromString(it.snapshotJson) }, dao.events(id), dao.rest(id))
    }
    suspend fun querySessions(cursor: SessionCursor?, limit: Int = 30, filter: RecordFilter = RecordFilter()): SessionPage<Summary> {
        require(limit in 1..100)
        filter.validate()
        val rows = dao.sessions(cursor?.startedWallUs, cursor?.id, limit + 1, filter.fromDate, filter.toDate, filter.status, filter.query)
        val visible = rows.take(limit)
        return SessionPage(visible.map { Summary(it, dao.media(it.id)) },
            if (rows.size > limit) visible.last().let { SessionCursor(it.startedWallUs, it.id) } else null)
    }
    suspend fun dailyStatistics(from: String, to: String): List<DailyStatistics> {
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
    suspend fun queryDeletableIds(protectedId: String?) = dao.deletableIds(protectedId)
    suspend fun evaluateOnce(id: String) {
        if (dao.evaluation(id) != null) return
        val session = checkNotNull(dao.session(id))
        val result = EvaluationEngine.evaluate(session, dao.events(id), dao.coverage(id), dao.rest(id), dao.revisions(id))
        dao.insertEvaluation(EvaluationEntity(id, result.version, Json.encodeToString(result)))
    }
}
