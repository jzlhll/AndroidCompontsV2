package com.au.agit.ui

import com.au.agit.ui.components.AGitButton as Button
import com.au.agit.ui.components.AGitTextButton as TextButton
import com.au.agit.ui.components.AGitIconButton as IconButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.au.agit.R
import com.au.agit.model.Catalog
import com.au.agit.model.GitProject
import com.au.agit.model.TokenGroup
import com.au.agit.model.AppTheme
import com.au.agit.ui.theme.AGitTheme
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeDimens
import com.au.module_androiduiex.styles.ComposeTypography

@Composable
fun ProjectDrawer(
    catalog: Catalog,
    selected: String?,
    onSelect: (String) -> Unit,
    onSelectGroup: (String) -> Unit,
    onClone: () -> Unit,
    onToken: (String?) -> Unit,
    onAuthor: () -> Unit,
    onSettings: () -> Unit,
    onTheme: () -> Unit,
) {
    val selectedGroup = catalog.groups.firstOrNull { group -> catalog.projects.any { it.id == selected && it.groupId == group.id } } ?: catalog.currentGroup
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(ComposeDimens.PaddingEdge20), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selectedGroup?.name ?: stringResource(R.string.app_name), style = ComposeTypography.Font20M,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (catalog.author.displayName.isNotBlank()) Text(catalog.author.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            if (catalog.groups.isNotEmpty()) IconButton(onClick = onClone) { Icon(Icons.Default.Add, stringResource(R.string.add_project)) }
        }
        if (catalog.groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { onToken(null) }) { Text(stringResource(R.string.add_token)) }
            }
        } else LazyColumn(Modifier.weight(1f)) {
            catalog.groups.forEach { group ->
                item(key = "group-${group.id}") {
                    val projects = catalog.projects.filter { it.groupId == group.id }
                    val currentGroup = selectedGroup?.id == group.id
                    var expanded by rememberSaveable { mutableStateOf(true) }
                    val arrowRotation by animateFloatAsState(if (expanded) 0f else -90f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "group expansion")
                    Row(Modifier.fillMaxWidth().padding(horizontal = ComposeDimens.PaddingEdge), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            contentPadding = PaddingValues(vertical = 7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ComposeDimens.PaddingEdge / 2), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(if (expanded) R.string.collapse_group else R.string.expand_group),
                                    modifier = Modifier.size(32.dp).rotate(arrowRotation),
                                    tint = if (currentGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(group.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = if (currentGroup) ComposeTypography.Font14M else ComposeTypography.Font14sp,
                                    color = if (currentGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (projects.isEmpty()) {
                            DrawerSelectionButton(currentGroup, onClick = { onSelectGroup(group.id) })
                        }
                        IconButton(onClick = { onToken(group.id) }, modifier = Modifier.widthIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_group_tune), stringResource(R.string.edit_token),
                                modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (expanded) projects.forEach { project ->
                        NavigationDrawerItem(label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = ComposeTypography.Font14sp, color = LocalContentColor.current) }, selected = selected == project.id,
                            badge = { DrawerSelectionButton(selected == project.id, onClick = { onSelect(project.id) }) },
                            onClick = { onSelect(project.id) }, modifier = Modifier.padding(start = 40.dp, end = 12.dp))
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = onAuthor, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.author_title), modifier = Modifier.fillMaxWidth().padding(horizontal = ComposeDimens.PaddingEdge / 2), fontSize = 15.sp) }
        TextButton(onClick = onTheme, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.appearance), modifier = Modifier.fillMaxWidth().padding(horizontal = ComposeDimens.PaddingEdge / 2)) }
        TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().padding(bottom = ComposeDimens.BottomMargin)) { Text(stringResource(R.string.settings), modifier = Modifier.fillMaxWidth().padding(horizontal = ComposeDimens.PaddingEdge / 2), fontSize = 15.sp) }
    }
}

@Composable
private fun DrawerSelectionButton(isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val buttonModifier = modifier.widthIn(min = 80.dp).heightIn(min = 54.dp)
    if (isCurrent) {
        Row(buttonModifier.semantics { selected = true }.padding(horizontal = ComposeDimens.PaddingEdge / 2),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.current), style = ComposeTypography.Font12M, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        TextButton(onClick = onClick, modifier = buttonModifier, contentPadding = PaddingValues(horizontal = ComposeDimens.PaddingEdge / 2)) {
            Text(stringResource(R.string.select), style = ComposeTypography.Font12M, color = LocalContentColor.current)
        }
    }
}

@Preview(showBackground = true, locale = "en")
@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun ProjectDrawerPreview() {
    AppPreview {
        AGitTheme(AppTheme.Black) {
            Surface(Modifier.width(320.dp).height(640.dp)) {
                ProjectDrawer(
                    catalog = Catalog(
                        groups = listOf(TokenGroup("personal", "Personal", ""), TokenGroup("work", "Work", "")),
                        projects = listOf(GitProject("preview", "personal", "AndroidComponts", "https://github.com/owner/repository")),
                        selectedGroupId = "personal", selectedProjectId = "preview",
                    ),
                    selected = "preview", onSelect = {}, onSelectGroup = {}, onClone = {}, onToken = {}, onAuthor = {}, onSettings = {}, onTheme = {},
                )
            }
        }
    }
}
