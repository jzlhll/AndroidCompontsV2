package com.au.module_androiduiex.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.views.ViewFragment

/**
 * Compose 页面的统一承载 Fragment：固定 FullImmersive，inset 由 Compose 界面自行处理。
 * 子类只实现 [ScreenContent]。
 */
abstract class ComposeViewFragment : ViewFragment() {

    /** 页面内容，inset（statusBarsPadding 等）在此自行处理。 */
    @Composable
    abstract fun ScreenContent()

    final override fun onUiCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ScreenContent()
            }
        }
    }

    final override fun immersiveMode(): ImmersiveMode = ImmersiveMode.FullImmersive()
}
