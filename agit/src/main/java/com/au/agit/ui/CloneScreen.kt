package com.au.agit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.au.agit.R
import com.au.agit.model.CloneMode
import com.au.agit.model.TokenGroup
import com.au.agit.ui.theme.AGitTheme
import com.au.agit.ui.theme.LocalThemePalette

@Composable
fun CloneScreen(
    group: TokenGroup?,
    busy: Boolean,
    onBack: () -> Unit,
    onClone: (String, String, CloneMode, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(CloneMode.Full) }
    var depth by rememberSaveable { mutableIntStateOf(10) }
    val depthVisible = mode == CloneMode.Depth
    val depthAlpha by animateFloatAsState(if (depthVisible) 1f else 0f, tween(180), label = "depth visibility")
    PageScaffold(stringResource(R.string.clone_title), onBack, modifier) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.clone_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(group?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.repository_url)) },
                modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            CloneModeSelector(mode, !busy, onModeChange = { mode = it })
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(when (mode) {
                    CloneMode.Full -> R.string.clone_full_description
                    CloneMode.Depth -> R.string.clone_depth_description
                    CloneMode.Download -> R.string.clone_download_description
                }), style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                DepthStepper(depth, !busy && depthVisible, onDepthChange = { depth = it },
                    modifier = Modifier.alpha(depthAlpha).then(if (depthVisible) Modifier else Modifier.clearAndSetSemantics {}))
                Spacer(Modifier.height(32.dp))
                Button(onClick = { group?.let { onClone(it.id, url, mode, depth) } }, enabled = !busy && group != null && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(painterResource(if (mode == CloneMode.Download) R.drawable.ic_download else R.drawable.ic_git_branch), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (mode == CloneMode.Download) R.string.download else R.string.clone))
                }
            }
        }
    }
}

@Composable
private fun CloneModeSelector(mode: CloneMode, enabled: Boolean, onModeChange: (CloneMode) -> Unit, modifier: Modifier = Modifier) {
    var distance by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 32.dp.toPx() }
    val direction = LocalLayoutDirection.current
    val noGitColors = LocalThemePalette.current.noGit
    BoxWithConstraints(modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).padding(4.dp).selectableGroup()
        .draggable(rememberDraggableState { distance += it }, Orientation.Horizontal, enabled = enabled,
            reverseDirection = direction == LayoutDirection.Rtl, onDragStarted = { distance = 0f }, onDragStopped = {
                val next = when {
                    distance < -threshold && mode.ordinal < CloneMode.entries.lastIndex -> mode.ordinal + 1
                    distance > threshold && mode.ordinal > 0 -> mode.ordinal - 1
                    else -> mode.ordinal
                }
                onModeChange(CloneMode.entries[next])
            })) {
        val segmentWidth = maxWidth / CloneMode.entries.size
        val offset by animateDpAsState(segmentWidth * mode.ordinal, tween(280), label = "clone mode selection")
        Box(Modifier.offset(x = offset).width(segmentWidth).height(44.dp).clip(CircleShape)
            .background(if (mode == CloneMode.Download) noGitColors.background else MaterialTheme.colorScheme.primaryContainer))
        Row(Modifier.fillMaxWidth()) {
            CloneMode.entries.forEach { option ->
                val selected = option == mode
                Row(Modifier.weight(1f).height(44.dp).clip(CircleShape)
                    .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { onModeChange(option) }),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    val tint = when {
                        option == CloneMode.Download -> noGitColors.foreground
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(painterResource(when (option) {
                        CloneMode.Full -> R.drawable.ic_git_branch
                        CloneMode.Depth -> R.drawable.ic_git_commit
                        CloneMode.Download -> R.drawable.ic_no_git
                    }), null, Modifier.size(17.dp), tint = tint)
                    Text(stringResource(when (option) {
                        CloneMode.Full -> R.string.clone_full
                        CloneMode.Depth -> R.string.clone_depth
                        CloneMode.Download -> R.string.no_git
                    }), style = MaterialTheme.typography.labelMedium, fontSize = 13.sp,
                        color = tint, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DepthStepper(depth: Int, enabled: Boolean, onDepthChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.depth_value, depth)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDepthChange(depth - 1) }, enabled = enabled && depth > 1,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.decrease_depth))
        }
        Text(depth.toString(), modifier = Modifier.widthIn(min = 64.dp).semantics { stateDescription = description },
            style = MaterialTheme.typography.titleMedium, fontSize = 17.sp,
            textAlign = TextAlign.Center)
        IconButton(onClick = { onDepthChange(depth + 1) }, enabled = enabled && depth < Int.MAX_VALUE,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.increase_depth))
        }
    }
}

@Preview(showBackground = true, locale = "en")
@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun ClonePreview() {
    AGitTheme { CloneScreen(TokenGroup("preview", "Personal", ""), false, {}, { _, _, _, _ -> }) }
}

@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun DepthSelectionPreview() {
    AGitTheme {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CloneModeSelector(CloneMode.Depth, true, {})
            DepthStepper(10, true, {})
        }
    }
}
