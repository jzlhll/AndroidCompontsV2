package com.au.module_androiduiex.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.au.module_androiduiex.styles.bindPreviewFontAssets

/**
 * 在子 Composable 组合前绑定 Preview assets，供 Studio Preview 加载字体等资源。
 */
@Composable
fun PreviewGlobalsHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember(context) {
        bindPreviewFontAssets(context.assets)
        context.assets
    }
    content()
}

/** Compose Preview 统一根容器，新增 @Preview 时作为最外层包裹。 */
@Composable
fun AppPreview(content: @Composable () -> Unit) {
    PreviewGlobalsHost(content = content)
}
