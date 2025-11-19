package com.au.appcompose.composeutils

import android.app.Activity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * 获取当前窗口的宽度模式
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun Activity.windowSizeMode() {
    val sizeClass = calculateWindowSizeClass(this)
    when (sizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // 紧凑型宽度（例如手机竖屏）：使用底部导航栏等布局
        }
        WindowWidthSizeClass.Medium -> {
            // 中等宽度（例如平板竖屏或折叠屏半展开）
        }
        WindowWidthSizeClass.Expanded -> {
            // 扩展宽度（例如平板横屏或折叠屏全展开）：使用导航抽屉等布局
        }
    }
}

/**
 * 屏幕的高宽，并不能代表应用当前的高宽，比如悬浮窗的状态
 */
@Composable
fun GetScreenDpValue(): Pair<Int, Int> {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    return Pair(screenWidthDp, screenHeightDp)
}

/**
 * 获取状态栏高度。只需要放在compose函数体即可获取。
 */
@Composable
fun getStatusBarHeight(density: Density = LocalDensity.current) : Dp {
    return WindowInsets.statusBars.getTop(density).pxToDp(density)
}

/**
 * 获取导航栏高度。只需要放在compose函数体即可获取。
 */
@Composable
fun getNavigationBarHeight(density: Density = LocalDensity.current) : Dp {
    return WindowInsets.navigationBars.getBottom(density).pxToDp(density)
}