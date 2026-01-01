package com.au.module_androidui.ui.base

/**
 * @author Allan
 * @date :2025/11/14
 */
interface IFullWindow {
    /**
     * 是否在默认的实现中，沉浸式状态，是否延伸到statusBar和navigationBar下。
     * 由于android15要求默认状态栏和导航栏是透明的，
     * 所以对于一般的应用需要将状态栏和导航栏padding出来。所以这里默认都是true。
     *
     * 对于activity，我们架构设计为使用FragmentShellActivity承载Fragment显示。
     * 因此，默认都有activity（从基类AbsActivity开始）都是完全沉浸式 FullImmersive。
     *
     * 对于Fragment，默认 ImmersiveMode.PaddingBars，让它padding掉头和尾。
     */
    fun immersiveMode() : ImmersiveMode
}
