package childmonitor.domain

import childmonitor.model.*
import kotlin.math.abs

/** 先采集用户确认区域的稳定空景，之后只接受与基准匹配的可见空座位。 */
class SeatVisibilityTracker {
    private var candidate: List<SceneCell>? = null
    private var candidateSinceUs: Long? = null
    private var lastUs: Long? = null
    private var reference: List<SceneCell>? = null
    val ready: Boolean get() = reference != null
    val progress: Float get() = if (ready) 1f else candidateSinceUs?.let { start ->
        ((lastUs ?: start) - start).toFloat() / (DefaultMonitorConfig.calibrationStableMs * 1_000)
    } ?: 0f

    fun reset() {
        candidate = null
        candidateSinceUs = null
        lastUs = null
        reference = null
    }

    fun observe(observation: Observation, region: SeatRegion): Boolean {
        val grid = observation.sceneGrid
        val cells = if (grid == null || grid.columns <= 0 || grid.rows <= 0 || grid.cells.size != grid.columns * grid.rows) emptyList()
        else grid.cells.filterIndexed { index, _ ->
            region.contains(Point((index % grid.columns + .5f) / grid.columns, (index / grid.columns + .5f) / grid.rows))
        }
        val occupied = observation.people.any { box ->
            box.left < region.right && box.right > region.left && box.top < region.bottom && box.bottom > region.top
        }
        val mean = cells.map { it.luminance }.average()
        val detail = cells.map { (it.luminance - mean) * (it.luminance - mean) + it.texture * it.texture }.average()
        val visible = observation.sceneClear && !observation.deviceMoved && cells.size >= 6 &&
            mean in 25.0..235.0 && detail > 100 && cells.all { cell ->
                listOf(cell.luminance, cell.redChroma, cell.blueChroma, cell.texture).all { it.isFinite() }
            }
        val consecutive = lastUs?.let { observation.frameTimeUs - it in 1..DefaultMonitorConfig.evidenceMaxGapMs * 1_000 } == true
        lastUs = observation.frameTimeUs
        val saved = reference
        if (saved != null) return visible && !occupied && matches(saved, cells)
        if (!visible || occupied) {
            candidate = null
            candidateSinceUs = null
            return false
        }
        if (!consecutive || candidate?.let { matches(it, cells) } != true) {
            candidate = cells
            candidateSinceUs = observation.frameTimeUs
        } else if (observation.frameTimeUs - checkNotNull(candidateSinceUs) >= DefaultMonitorConfig.calibrationStableMs * 1_000) {
            reference = candidate
            return true
        }
        return false
    }

    private fun matches(left: List<SceneCell>, right: List<SceneCell>): Boolean {
        if (left.size != right.size || left.isEmpty()) return false
        val matched = left.zip(right).count { (a, b) ->
            abs(a.luminance - b.luminance) <= 25 && abs(a.redChroma - b.redChroma) <= 20 &&
                abs(a.blueChroma - b.blueChroma) <= 20 && abs(a.texture - b.texture) <= 20
        }
        return matched.toFloat() / left.size >= .9f
    }
}
