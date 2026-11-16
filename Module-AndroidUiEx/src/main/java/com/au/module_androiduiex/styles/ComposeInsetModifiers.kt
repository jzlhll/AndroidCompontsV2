package com.au.module_androiduiex.styles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 导航栏安全区的跨设备视觉基线。
 *
 * 手势导航横条设备通常上报 24dp 左右，三键导航上报 48dp 左右；部分 ROM（如鸿蒙手势模式）不上报导航栏，四边均为 0。
 * 低于该定义的值时在导航栏实际所在边补齐差额，达到该值及以上保持系统原始值不做额外占位，
 */
private val NavBarInsetBaseline = 9.dp

/**
 * 读取导航栏 inset，返回需要补齐的 padding：
 * 竖屏导航栏在底部补 bottom，横屏三键导航在左右补对应方向；四边都未上报时按手势横条位于底部处理。
 *
 * 需要自行组装 padding 时使用本函数，返回值只包含需要补的一侧，其余方向为 0.dp；
 * 直接给 Modifier 用时优先选 [navBarInsetBaseline]。
 */
@Composable
fun navBarInsetShortfall(): PaddingValues {
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.navigationBars.asPaddingValues()
    val bottom = insets.calculateBottomPadding()
    val start = insets.calculateStartPadding(direction)
    val end = insets.calculateEndPadding(direction)

    // 导航栏所在边取已上报数值最大的一侧，避免只读 bottom 而漏掉横屏的左右导航栏。
    val bottomIsWidest = bottom >= start && bottom >= end
    val startIsWidest = !bottomIsWidest && start > end
    val widest = if (bottomIsWidest) bottom else if (startIsWidest) start else end
    if (widest >= NavBarInsetBaseline) return PaddingValues(0.dp)

    val shortfall = NavBarInsetBaseline - widest
    return when {
        bottomIsWidest -> PaddingValues(bottom = shortfall)
        startIsWidest -> PaddingValues(start = shortfall)
        else -> PaddingValues(end = shortfall)
    }
}

/**
 * 在导航栏所在边补齐安全区，用于 edge-to-edge 下需要贴近边缘、又不能压到导航栏的元素，
 * 例如悬浮按钮组、抽屉底部操作区、自定义底部栏。
 *
 * 引用：
 * ```kotlin
 * import com.au.module_androiduiex.styles.navBarInsetBaseline
 *
 * Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navBarInsetBaseline()) { ... }
 * ```
 *
 * 只补系统少给的那部分，不重复占位：Scaffold 的 FAB、ModalDrawerSheet、ModalBottomSheet 默认已经应用 systemBars，
 * 同一元素不要再叠加 navigationBarsPadding()。系统已上报足够边距时本 Modifier 不产生任何尺寸变化。
 */
@Composable
fun Modifier.navBarInsetBaseline(): Modifier = padding(navBarInsetShortfall())
