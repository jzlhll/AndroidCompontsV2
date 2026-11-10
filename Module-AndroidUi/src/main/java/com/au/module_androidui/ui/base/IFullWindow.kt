package com.au.module_androidui.ui.base

/**
 * @author Allan
 * @date :2025/11/14
 */
interface IFullWindow {
    /**
     * 声明页面的系统栏避让策略，Window 始终使用 Edge-to-Edge。
     * AbsActivity 统一在 android.R.id.content 通过 Insets 分发应用该策略。
     * FragmentShellActivity 使用宿主页 Fragment 的策略，嵌套 Fragment 不单独应用默认避让。
     * 默认 PaddingBars；FullImmersive 页面及 Compose 自行处理安全区域。
     */
    fun immersiveMode() : ImmersiveMode
}
