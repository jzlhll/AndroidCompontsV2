package com.au.agit.ui

import com.au.agit.ui.components.AGitButton as Button
import com.au.agit.ui.components.AGitOutlinedButton as OutlinedButton
import com.au.agit.ui.components.AGitTextButton as TextButton
import com.au.agit.ui.components.AGitIconButton as IconButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.au.agit.R
import com.au.agit.model.*
import com.au.agit.ui.theme.AGitTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val PageSlideAnimationSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

@Serializable data object HomeKey : NavKey
@Serializable data class RepositoryKey(val projectId: String, val directory: String = "") : NavKey
@Serializable data class CommitKey(val projectId: String) : NavKey
@Serializable data class TokenKey(val groupId: String? = null, val cloneAfterSave: Boolean = false) : NavKey
@Serializable data class CloneKey(val groupId: String? = null) : NavKey
@Serializable data class AuthorKey(val tokenGroupSetup: Boolean = false, val cloneAfterSave: Boolean = false) : NavKey
@Serializable data object LogsKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class LogFileKey(val name: String) : NavKey
@Serializable data object ThemeKey : NavKey
@Serializable data class MediaKey(val projectId: String, val path: String) : NavKey

@Composable
fun AGitRoot(model: AppViewModel = koinViewModel(), onReady: () -> Unit = {}) {
    val catalog by model.catalogFlow.collectAsStateWithLifecycle()
    val loadError by model.loadErrorFlow.collectAsStateWithLifecycle()
    if (catalog == null && loadError == null) return
    AGitTheme(catalog?.theme ?: AppTheme.Black) {
        AGitApp(model)
        SideEffect { onReady() }
    }
}

@Composable
fun AGitApp(model: AppViewModel = koinViewModel()) {
    val catalog by model.catalogFlow.collectAsStateWithLifecycle()
    val loadError by model.loadErrorFlow.collectAsStateWithLifecycle()
    val operation by model.operationFlow.collectAsStateWithLifecycle()
    val notice by model.noticeFlow.collectAsStateWithLifecycle()
    val actionBusy by model.actionBusyFlow.collectAsStateWithLifecycle()
    val savedForm by model.savedFormFlow.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(HomeKey)
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var choosingTokenGroup by rememberSaveable { mutableStateOf(false) }
    fun back() {
        if (backStack.size > 1) {
            val key = backStack.lastOrNull()
            if (key is TokenKey && key.groupId == null) model.clearTokenGroupDraft()
            backStack.removeAt(backStack.lastIndex)
        }
    }
    fun openProject(id: String) {
        model.select(id)
        backStack.clear()
        backStack.add(RepositoryKey(id))
        scope.launch { drawer.close() }
    }
    fun go(key: NavKey) { if (backStack.lastOrNull() != key) backStack.add(key) }
    fun addProject() {
        scope.launch { drawer.close() }
        if (catalog?.groups.isNullOrEmpty()) go(TokenKey()) else choosingTokenGroup = true
    }
    LaunchedEffect(savedForm?.id) {
        savedForm?.let { saved ->
            val key = backStack.lastOrNull()
            if (saved.kind == "author" && key is AuthorKey) back()
            if (saved.kind == "token_author" && key is AuthorKey && key.tokenGroupSetup) {
                back()
                if (backStack.lastOrNull() is TokenKey) back()
                if (key.cloneAfterSave && saved.savedGroupId != null) go(CloneKey(saved.savedGroupId))
            }
            if (saved.kind == "token" && key is TokenKey && key.groupId == saved.groupId) {
                back()
                if (key.cloneAfterSave && saved.savedGroupId != null) go(CloneKey(saved.savedGroupId))
            }
            model.acknowledgeSavedForm(saved.id)
        }
    }
    val currentCatalog = catalog
    LaunchedEffect(currentCatalog?.projects, currentCatalog?.selectedProjectId) {
        if (backStack.size == 1 && backStack[0] == HomeKey && !currentCatalog?.projects.isNullOrEmpty()) {
            val id = currentCatalog!!.selectedProjectId?.takeIf { selected -> currentCatalog.projects.any { it.id == selected } }
                ?: currentCatalog.projects.first().id
            backStack[0] = RepositoryKey(id)
        }
    }
    Surface(Modifier.fillMaxSize()) {
        if (currentCatalog == null) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                if (loadError == null) CircularProgressIndicator()
                else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!)
                    Button(onClick = model::loadCatalog) { Text(stringResource(R.string.retry)) }
                }
            }
        } else {
            val content: @Composable () -> Unit = {
                NavDisplay(
                    backStack = backStack,
                    onBack = { back() },
                    transitionSpec = {
                        slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { -it })
                    },
                    popTransitionSpec = {
                        slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = { _ ->
                        slideInHorizontally(PageSlideAnimationSpec, initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(PageSlideAnimationSpec, targetOffsetX = { it })
                    },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    entryProvider = entryProvider {
                        entry<HomeKey> {
                            HomeScreen(currentCatalog, onMenu = { scope.launch { drawer.open() } },
                                onAddToken = { go(TokenKey()) }, onClone = ::addProject, onProject = ::openProject)
                        }
                        entry<RepositoryKey> { key ->
                            val repositoryModel = koinViewModel<RepositoryViewModel>(parameters = { parametersOf(key.projectId) })
                            RepositoryRoute(key, currentCatalog, repositoryModel, model,
                                onBack = { back() }, onMenu = { scope.launch { drawer.open() } },
                                onDirectory = { go(RepositoryKey(key.projectId, it)) },
                                onCommit = { go(CommitKey(key.projectId)) }, onMedia = { go(MediaKey(key.projectId, it)) })
                        }
                        entry<CommitKey> { key ->
                            val repositoryModel = koinViewModel<RepositoryViewModel>(parameters = { parametersOf(key.projectId) })
                            CommitRoute(repositoryModel, model, currentCatalog.author, onBack = { back() }, onAuthor = { go(AuthorKey()) })
                        }
                        entry<TokenKey> { key ->
                            val needsAuthor = key.groupId == null && !currentCatalog.author.isConfigured
                            TokenScreen(currentCatalog.groups.firstOrNull { it.id == key.groupId }, actionBusy, needsAuthor, onBack = { back() },
                                onSave = { name, token ->
                                    if (needsAuthor) {
                                        if (model.prepareTokenGroup(name, token)) go(AuthorKey(tokenGroupSetup = true, cloneAfterSave = key.cloneAfterSave))
                                    } else model.saveGroup(name, token, key.groupId)
                                }, draft = if (key.groupId == null) model.tokenGroupInput() else null)
                        }
                        entry<CloneKey> { key ->
                            if (key.groupId == null) {
                                LaunchedEffect(key) { back(); choosingTokenGroup = true }
                            } else CloneScreen(currentCatalog.groups.firstOrNull { it.id == key.groupId }, actionBusy, onBack = { back() }, onClone = model::clone)
                        }
                        entry<AuthorKey> { key ->
                            if (key.tokenGroupSetup && model.tokenGroupInput() == null && savedForm?.kind != "token_author") {
                                LaunchedEffect(key) { model.notify(com.au.agit.appString(R.string.token_reenter)); back() }
                            } else AuthorScreen(currentCatalog.author, actionBusy, onBack = { back() },
                                onSave = if (key.tokenGroupSetup) model::saveTokenGroupWithAuthor else model::saveAuthor)
                        }
                        entry<LogsKey> {
                            val logsModel = koinViewModel<LogsViewModel>()
                            LogsRoute(logsModel, model, onBack = { back() }, onOpen = { go(LogFileKey(it)) })
                        }
                        entry<SettingsKey> { SettingsScreen(onBack = { back() }, onLogs = { go(LogsKey) }) }
                        entry<LogFileKey> { key ->
                            val logsModel = koinViewModel<LogsViewModel>(parameters = { parametersOf(key.name) })
                            LogFileRoute(key.name, logsModel, onBack = { back() })
                        }
                        entry<ThemeKey> { ThemeScreen(currentCatalog.theme, model::setTheme, onBack = { back() }) }
                        entry<MediaKey> { key -> MediaRoute(key, model, onBack = { back() }) }
                    },
                )
            }
            val selected = (backStack.lastOrNull() as? RepositoryKey)?.projectId ?: currentCatalog.selectedProjectId
            val drawerContent: @Composable () -> Unit = {
                ProjectDrawer(currentCatalog, selected,
                    onSelect = ::openProject, onClone = ::addProject,
                    onToken = { id -> scope.launch { drawer.close() }; go(TokenKey(id)) },
                    onAuthor = { scope.launch { drawer.close() }; go(AuthorKey()) }, onSettings = { scope.launch { drawer.close() }; go(SettingsKey) },
                    onTheme = { scope.launch { drawer.close() }; go(ThemeKey) })
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 840.dp) {
                    PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { drawerContent() } }) { content() }
                } else {
                    ModalNavigationDrawer(drawerState = drawer, drawerContent = { ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) { drawerContent() } }) { content() }
                }
            }
        }
        if (operation == null) NoticeHost(notice, model::acknowledgeNotice)
        if (choosingTokenGroup && currentCatalog != null) {
            AlertDialog(onDismissRequest = { choosingTokenGroup = false },
                title = { Text(stringResource(R.string.select_group)) },
                text = {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(currentCatalog.groups, key = { it.id }) { group ->
                            TextButton(onClick = { choosingTokenGroup = false; go(CloneKey(group.id)) }, modifier = Modifier.fillMaxWidth()) { Text(group.name) }
                        }
                        item {
                            OutlinedButton(onClick = { choosingTokenGroup = false; go(TokenKey(cloneAfterSave = true)) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.add_token))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { choosingTokenGroup = false }) { Text(stringResource(R.string.cancel)) } })
        }
        operation?.let { state ->
            OperationDialog(state, notice, model::acknowledgeNotice, model::cancelOperation,
                onClose = {
                    model.closeOperation()
                    if (state.status == OperationStatus.Success && state.kind == "clone") {
                        catalog?.selectedProjectId?.let(::openProject)
                    } else if (state.status == OperationStatus.Success && state.kind == "commit") back()
                }, onPush = model::continuePush)
        }
    }
}

@Composable
private fun ProjectDrawer(
    catalog: Catalog,
    selected: String?,
    onSelect: (String) -> Unit,
    onClone: () -> Unit,
    onToken: (String?) -> Unit,
    onAuthor: () -> Unit,
    onSettings: () -> Unit,
    onTheme: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(catalog.projects.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
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
                    var expanded by rememberSaveable { mutableStateOf(true) }
                    val arrowRotation by animateFloatAsState(if (expanded) 0f else -90f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "group expansion")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(if (expanded) R.string.collapse_group else R.string.expand_group),
                                    modifier = Modifier.size(24.dp).rotate(arrowRotation))
                                Text(group.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { onToken(group.id) }, modifier = Modifier.padding(end = 12.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.edit_token)) }
                    }
                    if (expanded) catalog.projects.filter { it.groupId == group.id }.forEach { project ->
                        NavigationDrawerItem(label = { Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = selected == project.id,
                            badge = { if (selected == project.id) Icon(Icons.Default.Check, null) },
                            onClick = { onSelect(project.id) }, modifier = Modifier.padding(start = 40.dp, end = 12.dp))
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(onClick = onAuthor, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.author_title), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
        TextButton(onClick = onTheme, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.appearance), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
        TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
    }
}
