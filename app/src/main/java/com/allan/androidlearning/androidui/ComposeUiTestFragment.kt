package com.allan.androidlearning.androidui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.allan.classnameanno.EntryFrgName
import com.au.module_androiduiex.styles.ComposeLineTabLayout
import kotlinx.coroutines.launch

/** Compose 版本 ViewPager 与 TabLayout 测试页。 */
@EntryFrgName(customName = "ComposeUiTest")
class ComposeUiTestFragment : Fragment() {
    private val pages = listOf(
        Pair("Components", AndroidUi1Fragment::class.java),
        Pair("Action", AndroidUi2Fragment::class.java),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeUiTestScreen(
                    fragmentManager = childFragmentManager,
                    pages = pages,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposeUiTestScreen(
    fragmentManager: FragmentManager,
    pages: List<Pair<String, Class<out Fragment>>>,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(3.dp))
        ComposeLineTabLayout(
            tabs = pages.map { it.first },
            selectedIndex = pagerState.currentPage,
        ) { index ->
            coroutineScope.launch {
                pagerState.animateScrollToPage(index)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ComposeFragmentPage(
                fragmentManager = fragmentManager,
                fragmentClass = pages[page].second,
            )
        }
    }
}

@Composable
private fun ComposeFragmentPage(
    fragmentManager: FragmentManager,
    fragmentClass: Class<out Fragment>,
) {
    val containerId = remember { View.generateViewId() }
    val fragmentTag = remember { "compose_ui_page_$containerId" }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { container ->
            container.post {
                if (!container.isAttachedToWindow || fragmentManager.isDestroyed ||
                    fragmentManager.findFragmentById(container.id) != null
                ) {
                    return@post
                }
                val fragment = fragmentClass.getDeclaredConstructor().newInstance()
                fragmentManager.beginTransaction()
                    .replace(container.id, fragment, fragmentTag)
                    .commitNowAllowingStateLoss()
            }
        },
    )

    DisposableEffect(containerId) {
        onDispose {
            if (fragmentManager.isDestroyed) {
                return@onDispose
            }
            fragmentManager.findFragmentByTag(fragmentTag)?.let { fragment ->
                fragmentManager.beginTransaction()
                    .remove(fragment)
                    .commitNowAllowingStateLoss()
            }
        }
    }
}
