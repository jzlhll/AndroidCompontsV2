package com.au.module_androidui.ui.base

/**
 * 如果你想要自行管控区域就用FullImmersive，会给你回调自行处理。
 */
sealed class ImmersiveMode {
    /**
     * 默认模式：两个bar都进行padding，就是显示在标准范围内。
     */
    object PaddingBars : ImmersiveMode()

    /**
     * 布局底部padding掉statusBar的距离
     */
    object PaddingStatusBar : ImmersiveMode()

    /**
     * 布局底部padding掉navBar的距离
     */
    object PaddingNavigationBar : ImmersiveMode()

    /**
     * 不padding，就是完全沉浸式。需要自行处理顶部和底部的距离问题
     */
    class FullImmersive(val barsHeightCallback:(statusBarHeight:Int, navBarHeight:Int)->Unit) : ImmersiveMode()

    fun isPaddingNavigationBar() =
        this == PaddingBars || this == PaddingNavigationBar

    fun isPaddingStatusBar() =
        this == PaddingBars || this == PaddingStatusBar
}