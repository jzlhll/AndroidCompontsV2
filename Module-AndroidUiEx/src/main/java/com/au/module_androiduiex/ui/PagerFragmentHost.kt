package com.au.module_androiduiex.ui

import androidx.annotation.IdRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager

/**
 * Compose Pager 中承载 Fragment 子页的桥接组件。
 *
 * 优先直接在 Pager 内写 Composable；只有历史页面或特殊页面必须复用 Fragment 时再用这个 Host。
 * [containerId] 必须传稳定的资源 id，不能用 View.generateViewId() 或 remember 生成。Activity/Fragment
 * 重建后 FragmentManager 会按 tag/id 恢复子 Fragment；如果 Compose 重新生成了新容器 id，旧 Fragment
 * 可能已经存在但没有挂到新 FragmentContainerView 上，导致页面空白。
 */
@Composable
fun PagerFragmentHost(
    fragmentManager: FragmentManager,
    tag: String,
    @IdRes containerId: Int,
    createFragment: () -> Fragment,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply { id = containerId }
        },
        update = { view ->
            if (fragmentManager.findFragmentByTag(tag) == null) {
                fragmentManager.beginTransaction()
                    .replace(view.id, createFragment(), tag)
                    .commitAllowingStateLoss()
            }
        },
    )
}
