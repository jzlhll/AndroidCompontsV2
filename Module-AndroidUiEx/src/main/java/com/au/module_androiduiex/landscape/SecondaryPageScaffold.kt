package com.au.module_androiduiex.landscape

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.navBarInsetBaseline
import com.au.module_androiduiex.styles.ComposeButton as Button
import com.au.module_androiduiex.styles.ComposeIconButton as IconButton
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton

/** 二级页面竖屏使用顶栏，横屏使用操作侧栏，并统一处理主要按钮和内容安全区。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryPageScaffold(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    titleStyle: TextStyle = ComposeTypography.Font20M,
    sidePaneFraction: Float = 0.3f,
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
    primaryActionEnabled: Boolean = true,
    loading: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (landscape) WindowInsets.safeDrawing else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!landscape) TopAppBar(
                expandedHeight = 64.dp,
                title = { Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = backEnabled) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, backContentDescription)
                    }
                },
                actions = {
                    actions()
                    if (primaryActionLabel != null) {
                        TextButton(onClick = onPrimaryAction, enabled = primaryActionEnabled) { Text(primaryActionLabel) }
                    }
                },
            )
        },
    ) { padding ->
        LandscapePaneLayout(
            showSidePane = landscape,
            sidePane = {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            IconButton(onClick = onBack, enabled = backEnabled) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, backContentDescription)
                            }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = titleStyle, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            actions()
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    if (primaryActionLabel != null) {
                        Button(onClick = onPrimaryAction, enabled = primaryActionEnabled,
                            modifier = Modifier.fillMaxWidth().padding(12.dp).navBarInsetBaseline()) {
                            Text(primaryActionLabel)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            sidePaneFraction = sidePaneFraction,
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!landscape && loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}
