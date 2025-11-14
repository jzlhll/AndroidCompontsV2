package com.au.module_android.ui.base

enum class ImmersiveMode {
    /**
     * 默认模式：两个bar都进行padding，就是显示在标准范围内。
     */
    PaddingBars,
    /**
     * 布局底部padding掉statusBar的距离
     */
    PaddingStatusBar,

    /**
     * 布局底部padding掉navBar的距离
     */
    PaddingNavigationBar,

    /**
     * 不padding，就是完全沉浸式。需要自行处理顶部和底部的距离问题
     * 可以使用View.post或者setOnApplyWindowInsetsListener监听insets
     */
    FullImmersive,
}

fun ImmersiveMode.isPaddingNavigationBar() =
    this == ImmersiveMode.PaddingBars || this == ImmersiveMode.PaddingNavigationBar
fun ImmersiveMode.isPaddingStatusBar() =
    this == ImmersiveMode.PaddingBars || this == ImmersiveMode.PaddingStatusBar