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
     * 避让顶部系统栏。
     */
    object PaddingStatusBar : ImmersiveMode()

    /**
     * 避让底部系统栏。
     */
    object PaddingNavigationBar : ImmersiveMode()

    /**
     * 容器不增加 padding，完整 Insets 继续向页面分发，Compose 也在页面内处理。
     * 回调提供顶部和底部安全间距，在 View 创建后、绘制前同步执行；重复分发时应幂等更新。
     * 此时 View 不一定已经完成测量，依赖宽高的计算应使用布局回调。
     */
    class FullImmersive(val barsHeightCallback:((statusBarHeight:Int, navBarHeight:Int)->Unit)?=null) : ImmersiveMode()

    fun isPaddingNavigationBar() =
        this == PaddingBars || this == PaddingNavigationBar

    fun isPaddingStatusBar() =
        this == PaddingBars || this == PaddingStatusBar
}
