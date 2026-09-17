package com.au.module_androiduiex.styles

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView

/**
 * 根据系统提供的左右手势区域避让返回手势，不使用固定宽度，也不改变内容布局。
 * 将触点转换到窗口坐标后判断起点，容器存在水平偏移时也能正确识别系统边缘。
 * 边缘起手时消费整段应用内指针事件，防止翻页、图片拖动等操作与系统返回同时响应。
 * 内部起手的手势保持原有处理，即使途中滑到边缘也不会被拦截，且这里的消费不会屏蔽系统返回。
 * 使用时放在应用手势处理 Modifier 之前，让边缘判断先于下游手势识别执行。
 * 未来 XML/View 版可由通用容器读取 WindowInsetsCompat.Type.systemGestures()，在 dispatchTouchEvent() 中按 ACTION_DOWN 的窗口坐标识别边缘起手，并消费整段事件以阻止向子 View 分发。
 */
@Composable
fun Modifier.respectSystemBackGestures(): Modifier {
    val systemGestures = WindowInsets.systemGestures
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    var windowLeft by remember { mutableFloatStateOf(0f) }
    return onGloballyPositioned { windowLeft = it.positionInWindow().x }
        .pointerInput(systemGestures, density, layoutDirection, view) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val left = systemGestures.getLeft(density, layoutDirection)
                val right = systemGestures.getRight(density, layoutDirection)
                val windowX = windowLeft + down.position.x
                if (windowX >= left && windowX < view.rootView.width - right) {
                    return@awaitEachGesture
                }
                // 仅按起点避让；消费应用内事件不会拦截系统手势。
                down.consume()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
}
