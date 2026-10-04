package childmonitor.domain

import childmonitor.model.*
import kotlin.math.abs

/** 自动保留完整人物入镜时的全画面基准，区分人物消失和背景物体变化。 */
class SceneChangeTracker {
    data class Change(val absent: Boolean = false, val backgroundChanged: Boolean = false)
    private var reference: SceneGrid? = null
    private var person: SeatRegion? = null
    private var objects: List<DetectedObject> = emptyList()

    fun reset() { reference = null; person = null; objects = emptyList() }

    fun confirmChange(observation: Observation) {
        reference = observation.sceneGrid
        person = observation.people.singleOrNull()
        objects = observation.objects
    }

    fun observe(observation: Observation, subjectPresent: Boolean, rememberSubject: Boolean): Change {
        val grid = observation.sceneGrid ?: return Change()
        if (grid.columns <= 0 || grid.rows <= 0 || grid.cells.size != grid.columns * grid.rows ||
            !observation.sceneClear || observation.deviceMoved || grid.cells.any { cell ->
                !cell.luminance.isFinite() || !cell.redChroma.isFinite() || !cell.blueChroma.isFinite() || !cell.texture.isFinite()
            }) return Change()
        val saved = reference
        val box = person
        val objectsChanged = saved != null && !matches(objects, observation.objects)
        var change = Change(backgroundChanged = objectsChanged)
        if (saved != null && box != null && saved.columns == grid.columns && saved.rows == grid.rows) {
            var foreground = 0
            var foregroundChanges = 0
            var background = 0
            var backgroundChanges = 0
            grid.cells.forEachIndexed { index, cell ->
                val point = Point((index % grid.columns + .5f) / grid.columns, (index / grid.columns + .5f) / grid.rows)
                val before = saved.cells[index]
                val changed = abs(before.luminance - cell.luminance) > 25 || abs(before.redChroma - cell.redChroma) > 20 ||
                    abs(before.blueChroma - cell.blueChroma) > 20 || abs(before.texture - cell.texture) > 20
                if (box.contains(point)) {
                    foreground++
                    if (changed) foregroundChanges++
                } else if (observation.people.none { it.contains(point) }) {
                    background++
                    if (changed) backgroundChanges++
                }
            }
            val stableBackground = background >= 12 && backgroundChanges.toFloat() / background <= .1f
            change = Change(absent = !subjectPresent && !objectsChanged && foreground >= 6 && foregroundChanges.toFloat() / foreground >= .5f && stableBackground,
                backgroundChanged = objectsChanged || background >= 12 && backgroundChanges.toFloat() / background >= .15f)
        }
        // 变化确认前保留旧基准；确认后由引擎接纳新场景，避免旧物体状态一直阻碍离开判断。
        if (rememberSubject) {
            if (saved == null) confirmChange(observation)
            else if (!change.backgroundChanged) {
                reference = grid
                person = observation.people.singleOrNull()
            }
        }
        return change
    }

    private fun matches(reference: List<DetectedObject>, current: List<DetectedObject>): Boolean {
        if (reference.size != current.size) return false
        val remaining = current.toMutableList()
        for (before in reference) {
            val index = remaining.indexOfFirst { after ->
                before.category == after.category &&
                    abs(before.bounds.left - after.bounds.left) < .08f && abs(before.bounds.top - after.bounds.top) < .08f &&
                    abs(before.bounds.right - after.bounds.right) < .08f && abs(before.bounds.bottom - after.bounds.bottom) < .08f
            }
            if (index < 0) return false
            remaining.removeAt(index)
        }
        return true
    }
}
