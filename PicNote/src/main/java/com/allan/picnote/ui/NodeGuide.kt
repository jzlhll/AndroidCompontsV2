package com.allan.picnote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.allan.picnote.data.NodeGuideKind
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.ComposeButton as Button

data class NodeGuide(val documentId: String, val kind: NodeGuideKind)

/** 记录目标编号或卡片的真实窗口位置，仅在气泡布局和绘制阶段读取。 */
class NodeGuideAnchor {
    var bounds: IntRect? by mutableStateOf(null)
    private var viewport: IntRect? = null
    private val cards = mutableMapOf<Int, IntRect>()

    fun updateViewport(value: IntRect) {
        viewport = value
        updateCardTarget()
    }

    fun updateCard(index: Int, value: IntRect) {
        cards[index] = value
        updateCardTarget()
    }

    fun removeCard(index: Int) {
        cards.remove(index)
        updateCardTarget()
    }

    private fun updateCardTarget() {
        val viewport = viewport ?: return
        val visible = cards.filterValues { it.overlaps(viewport) }
        val target = visible[2] ?: visible.filterKeys { it >= 2 }.minByOrNull { it.key }?.value
            ?: visible.minByOrNull { it.key }?.value
        bounds = target?.intersect(viewport)
    }
}

/** 在测量与绘制间共享气泡位置，不用坐标状态驱动整页重组。 */
private class NodeGuideGeometry {
    var bubble = Rect.Zero
}

@Composable
fun NodeGuideOverlay(anchor: NodeGuideAnchor, kind: NodeGuideKind, saving: Boolean, onAcknowledge: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(
        dismissOnBackPress = false, dismissOnClickOutside = false,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        val scheme = MaterialTheme.colorScheme
        val lightBars = scheme.background.luminance() > 0.5f
        SideEffect {
            window.setDimAmount(0f)
            window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        val geometry = remember { NodeGuideGeometry() }
        val insets = WindowInsets.safeDrawing
        val bubbleColor = scheme.surfaceContainerHigh
        Layout(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                .semantics { paneTitle = "节点操作提示" }
                .drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        val target = anchor.bounds
                        val bubble = geometry.bubble
                        if (target != null && bubble.width > 0f) {
                            path.rewind()
                            if (kind == NodeGuideKind.Menu) {
                                var centerY = target.center.y.toFloat()
                                val inset = 12.dp.toPx()
                                if (centerY < bubble.top + inset) centerY = bubble.top + inset
                                if (centerY > bubble.bottom - inset) centerY = bubble.bottom - inset
                                path.moveTo(bubble.left + 1f, centerY - 6.dp.toPx())
                                path.lineTo(target.right + 2.dp.toPx(), target.center.y.toFloat())
                                path.lineTo(bubble.left + 1f, centerY + 6.dp.toPx())
                            } else {
                                var centerX = target.center.x.toFloat()
                                val inset = 16.dp.toPx()
                                if (centerX < bubble.left + inset) centerX = bubble.left + inset
                                if (centerX > bubble.right - inset) centerX = bubble.right - inset
                                val down = target.center.y > bubble.center.y
                                val edge = if (down) bubble.bottom - 1f else bubble.top + 1f
                                path.moveTo(centerX - 6.dp.toPx(), edge)
                                path.lineTo(centerX, edge + if (down) 10.dp.toPx() else -10.dp.toPx())
                                path.lineTo(centerX + 6.dp.toPx(), edge)
                            }
                            path.close()
                            drawPath(path, bubbleColor)
                        }
                    }
                },
            content = {
                Surface(color = bubbleColor, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, scheme.outlineVariant)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(if (kind == NodeGuideKind.Menu) "单击左侧的节点，可以弹出操作菜单。" else "点击卡片，可以显示更多操作。", color = scheme.onSurface, style = ComposeTypography.Font14sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onAcknowledge, enabled = !saving, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                            Text("知道了")
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val target = anchor.bounds
            val margin = 16.dp.roundToPx()
            val left = insets.getLeft(this, layoutDirection) + margin
            val right = constraints.maxWidth - insets.getRight(this, layoutDirection) - margin
            val top = insets.getTop(this) + margin
            val bottom = constraints.maxHeight - insets.getBottom(this) - margin
            var x = if (kind == NodeGuideKind.Menu) (target?.right ?: left) + 12.dp.roundToPx() else left
            if (x < left) x = left
            if (x >= right) x = right - 1
            var width = right - x
            if (width > 288.dp.roundToPx()) width = 288.dp.roundToPx()
            if (width < 1) width = 1
            var availableHeight = bottom - top
            if (availableHeight < 1) availableHeight = 1
            val bubble = measurables.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = availableHeight))
            var y = if (kind == NodeGuideKind.Menu || target == null) (target?.center?.y ?: top) - bubble.height / 2 else {
                x = left + (right - left - bubble.width) / 2
                val gap = 12.dp.roundToPx()
                when {
                    target.bottom + gap + bubble.height <= bottom -> target.bottom + gap
                    target.top - gap - bubble.height >= top -> target.top - gap - bubble.height
                    target.center.y > (top + bottom) / 2 -> top
                    else -> bottom - bubble.height
                }
            }
            if (y + bubble.height > bottom) y = bottom - bubble.height
            if (y < top) y = top
            geometry.bubble = if (target == null) Rect.Zero else Rect(x.toFloat(), y.toFloat(), (x + bubble.width).toFloat(), (y + bubble.height).toFloat())
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (target != null) bubble.place(x, y)
            }
        }
    }
}
