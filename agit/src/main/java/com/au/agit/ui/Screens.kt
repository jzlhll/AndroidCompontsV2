package com.au.agit.ui

import com.au.agit.ui.components.AGitButton as Button
import com.au.agit.ui.components.AGitOutlinedButton as OutlinedButton
import com.au.agit.ui.components.AGitTextButton as TextButton
import com.au.agit.ui.components.AGitIconButton as IconButton
import android.content.ClipData
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.au.agit.R
import com.au.agit.data.AGitRepository
import com.au.agit.appString
import com.au.agit.model.*
import com.au.agit.ui.theme.AGitTheme
import com.au.agit.ui.components.FileStatusBadge
import com.au.agit.ui.components.FileTypeIcon
import com.au.agit.ui.components.changeColor
import com.au.agit.ui.components.changeBackground
import com.au.agit.ui.components.formatFileSize
import com.au.module_android.log.logd
import com.au.module_android.utilsmedia.getUriFromFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(title, maxLines = 1) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        }, actions = actions)
    }, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    catalog: Catalog,
    onMenu: () -> Unit,
    onAddToken: () -> Unit,
    onClone: () -> Unit,
    onProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) }, navigationIcon = {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, stringResource(R.string.menu)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max = 960.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(if (catalog.projects.isEmpty()) R.string.home_no_repositories else R.string.home_repositories), style = MaterialTheme.typography.headlineMedium)
                if (catalog.projects.isEmpty()) {
                    Text(stringResource(if (catalog.groups.isEmpty()) R.string.home_connect_description else R.string.home_clone_description),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                }
                Button(onClick = if (catalog.groups.isEmpty()) onAddToken else onClone) {
                    Text(stringResource(if (catalog.groups.isEmpty()) R.string.add_token else R.string.add_project))
                }
            }
            items(catalog.projects, key = { it.id }) { project ->
                OutlinedCard(onClick = { onProject(project.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleLarge)
                        Text(project.url.removePrefix("https://github.com/").removeSuffix(".git"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun TokenScreen(group: TokenGroup?, busy: Boolean, needsAuthor: Boolean, onBack: () -> Unit, onSave: (String, String) -> Unit, modifier: Modifier = Modifier, draft: Pair<String, String>? = null) {
    var name by rememberSaveable(group?.id) { mutableStateOf(group?.name ?: draft?.first.orEmpty()) }
    // 凭据不进入 SavedState、导航参数或 Preview。
    var token by remember(group?.id) { mutableStateOf(draft?.second.orEmpty()) }
    PageScaffold(stringResource(if (group == null) R.string.add_token else R.string.edit_token), onBack, modifier) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.token_access_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.token_name)) }, supportingText = { Text(stringResource(R.string.token_name_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.token_secret)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = { onSave(name, token) }, enabled = !busy && name.isNotBlank() && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (needsAuthor) R.string.next else R.string.save)) }
        }
    }
}

@Composable
fun CloneScreen(group: TokenGroup?, busy: Boolean, onBack: () -> Unit, onClone: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var url by rememberSaveable { mutableStateOf("") }
    PageScaffold(stringResource(R.string.add_project), onBack, modifier) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.clone_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(group?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.repository_url)) }, placeholder = { Text(stringResource(R.string.repository_url_example)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Button(onClick = { group?.let { onClone(it.id, url) } }, enabled = !busy && group != null && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clone)) }
        }
    }
}

@Composable
fun AuthorScreen(author: Author, busy: Boolean, onBack: () -> Unit, onSave: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf(author.name) }
    var email by rememberSaveable { mutableStateOf(author.email) }
    PageScaffold(stringResource(R.string.author_title), onBack, modifier) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.author_identity_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.author_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.author_email)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Button(onClick = { onSave(name, email) }, enabled = !busy && Author(name, email).isConfigured, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun RepositoryRoute(
    key: RepositoryKey,
    catalog: Catalog,
    model: RepositoryViewModel,
    app: AppViewModel,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onDirectory: (String) -> Unit,
    onCommit: () -> Unit,
    onMedia: (String) -> Unit,
) {
    val state by model.stateFlow.collectAsStateWithLifecycle()
    val project = catalog.projects.firstOrNull { it.id == key.projectId }
    val activeEdit = catalog.edits.any { it.projectId == key.projectId && it.active }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openFile by remember { mutableStateOf<RepositoryFile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refresh(key.directory) }
    RepositoryRefresh(model, app.repository, key.directory)
    fun openExternal(file: RepositoryFile, edit: Boolean) {
        openFile = null
        scope.launch {
            try {
                val source = if (edit) app.repository.beginEdit(key.projectId, file.path) else app.repository.file(key.projectId, file.path)
                val uri = getUriFromFile(context, source) ?: throw AGitException(appString(R.string.open_failed), "OPEN_FAILED")
                val mime = fileMime(source)
                val intent = Intent(if (edit) Intent.ACTION_EDIT else Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    clipData = ClipData.newRawUri(source.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (edit) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                launcher.launch(intent)
                app.logd { "external file opened project=${key.projectId} edit=$edit" }
            } catch (error: CancellationException) { throw error } catch (error: android.content.ActivityNotFoundException) {
                if (edit) app.repository.finishEditing(key.projectId)
                app.notify(appString(R.string.no_handler))
                app.failure("external file open", AGitException(appString(R.string.no_handler), "NO_HANDLER"), key.projectId)
            } catch (error: Exception) { app.failure("external file open", error, key.projectId) }
        }
    }
    RepositoryScreen(project?.name.orEmpty(), state, activeEdit, onBack, onMenu, onDirectory,
        onFile = { file ->
            val mime = fileMime(File(file.name))
            if (mime.startsWith("image/") || mime.startsWith("video/")) onMedia(file.path) else openFile = file
        }, onRefresh = { model.refresh(key.directory) }, onCommit = onCommit,
        onUpdate = { app.update(key.projectId) }, onPush = { app.preparePush(key.projectId) },
        onFinishEdit = { app.finishEditing(key.projectId) })
    openFile?.let { file ->
        AlertDialog(onDismissRequest = { openFile = null }, title = { Text(file.name) },
            text = { Text(file.path, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = { openExternal(file, true) }) { Text(stringResource(R.string.edit_file)) } },
            dismissButton = { TextButton(onClick = { openExternal(file, false) }) { Text(stringResource(R.string.view_file)) } })
    }
    if (!activeEdit) state.candidates.firstOrNull()?.let { candidate ->
        AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.copy_title)) },
            text = { Text(stringResource(R.string.copy_body, candidate.original, candidate.candidate)) },
            confirmButton = { TextButton(onClick = { app.resolveCandidate(candidate, true) }) { Text(stringResource(R.string.replace)) } },
            dismissButton = { TextButton(onClick = { app.resolveCandidate(candidate, false) }) { Text(stringResource(R.string.keep_both)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryScreen(
    projectName: String,
    state: RepositoryUiState,
    activeEdit: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onDirectory: (String) -> Unit,
    onFile: (RepositoryFile) -> Unit,
    onRefresh: () -> Unit,
    onCommit: () -> Unit,
    onUpdate: () -> Unit,
    onPush: () -> Unit,
    onFinishEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(projectName, maxLines = 1) }, navigationIcon = {
            IconButton(onClick = if (state.directory.isEmpty()) onMenu else onBack) {
                if (state.directory.isEmpty()) Icon(Icons.Default.Menu, stringResource(R.string.menu)) else Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        }, actions = { IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) } })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUpdate, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.update)) }
                Button(onClick = onCommit, enabled = !state.loading && state.snapshot?.dirty == true && !activeEdit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.commit)) }
                OutlinedButton(onClick = onPush, enabled = !state.loading && state.snapshot?.head != null, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.push)) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            state.snapshot?.let { snapshot ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(snapshot.branch, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                        if (snapshot.ahead != null && snapshot.behind != null && (snapshot.ahead > 0 || snapshot.behind > 0)) {
                            Text("↑${snapshot.ahead}  ↓${snapshot.behind}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    snapshot.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (activeEdit) Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.external_edit_active), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onFinishEdit) { Text(stringResource(R.string.finish_edit)) }
                }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.files)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.changes)) })
            }
            if (tab == 0) {
                Text("/" + state.directory, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.weight(1f)) {
                    if (state.files.isEmpty() && !state.loading) item { Text(stringResource(R.string.empty_directory), modifier = Modifier.padding(24.dp)) }
                    items(state.files, key = { it.path }) { file ->
                        val change = state.snapshot?.changes?.firstOrNull { file.path in it.paths }
                        val nestedCount = if (file.directory) state.snapshot?.changes?.count { item -> item.paths.any { it.startsWith(file.path + "/") } } ?: 0 else 0
                        val tint = if (change != null) changeColor(change.type) else if (nestedCount > 0) changeColor(ChangeType.Modified) else MaterialTheme.colorScheme.onSurface
                        val changed = change != null || nestedCount > 0
                        ListItem(headlineContent = { Text(file.name, textDecoration = if (!file.exists && !file.directory) TextDecoration.LineThrough else null) },
                            supportingContent = if (!file.directory && file.exists) ({ Text(formatFileSize(file.size), style = MaterialTheme.typography.bodySmall) }) else null,
                            leadingContent = { FileTypeIcon(file) },
                            colors = ListItemDefaults.colors(containerColor = if (changed) changeBackground(change?.type ?: ChangeType.Modified) else MaterialTheme.colorScheme.surface, headlineColor = tint),
                            trailingContent = {
                                if (change != null) FileStatusBadge(change)
                                else if (nestedCount > 0) Text(nestedCount.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable(enabled = file.exists || file.directory) { if (file.directory) onDirectory(file.path) else onFile(file) })
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    val changes = state.snapshot?.changes.orEmpty()
                    if (changes.isEmpty()) item { Text(stringResource(R.string.no_changes), modifier = Modifier.padding(24.dp)) }
                    items(changes, key = { it.path }) { change ->
                        ListItem(headlineContent = { Text(change.path, fontFamily = FontFamily.Monospace) },
                            supportingContent = { Text(change.kind) }, trailingContent = { FileStatusBadge(change) },
                            colors = ListItemDefaults.colors(containerColor = changeBackground(change.type), headlineColor = changeColor(change.type)))
                    }
                }
            }
        }
    }
}

@Composable
fun CommitRoute(model: RepositoryViewModel, app: AppViewModel, author: Author, onBack: () -> Unit, onAuthor: () -> Unit) {
    val state by model.stateFlow.collectAsStateWithLifecycle()
    RepositoryRefresh(model, app.repository)
    CommitScreen(state, author, onBack, onAuthor, onRefresh = { model.refresh() }, onCommit = { selected, message ->
        app.commit(model.projectId, selected, state.fingerprints.filterKeys { it in selected }, message)
    })
}

@Composable
private fun RepositoryRefresh(model: RepositoryViewModel, repository: AGitRepository, directory: String = "") {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, model, directory) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.refresh(directory)
            try {
                repository.changesFlow.collect { if (it == model.projectId) model.refresh(directory) }
            } finally { model.cancelRefresh() }
        }
    }
}

@Composable
fun CommitScreen(
    state: RepositoryUiState,
    author: Author,
    onBack: () -> Unit,
    onAuthor: () -> Unit,
    onRefresh: () -> Unit,
    onCommit: (Set<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var message by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val changes = state.snapshot?.changes.orEmpty()
    PageScaffold(stringResource(R.string.commit_title), onBack, modifier, actions = {
        IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onAuthor) { Text(if (!author.isConfigured) stringResource(R.string.author_title) else if (author.name.isBlank()) author.email else "${author.name} <${author.email}>") }
            OutlinedTextField(message, { message = it }, label = { Text(stringResource(R.string.commit_message)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            LazyColumn(Modifier.weight(1f)) {
                items(changes, key = { it.path }) { change ->
                    val checked = change.path in selected
                    ListItem(headlineContent = { Text(change.path, fontFamily = FontFamily.Monospace) }, supportingContent = { Text(change.kind + if (change.staged) " · " + stringResource(R.string.staged) else "") }, trailingContent = { FileStatusBadge(change) },
                        colors = ListItemDefaults.colors(containerColor = changeBackground(change.type), headlineColor = changeColor(change.type)),
                        leadingContent = { Checkbox(checked, { selected = if (it) selected + change.path else selected - change.path }) },
                        modifier = Modifier.clickable { selected = if (checked) selected - change.path else selected + change.path })
                }
            }
            val selectedPaths = changes.filter { it.path in selected }.flatMap { it.paths }.toSet()
            Button(onClick = { onCommit(selectedPaths, message) }, enabled = !state.loading && selectedPaths.isNotEmpty() && message.isNotBlank() && author.isConfigured, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.selected_count, changes.count { it.path in selected }))
            }
        }
    }
}

fun fileMime(file: File): String {
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: when (file.extension.lowercase()) {
        "kt", "kts", "java", "md", "json", "yaml", "yml", "toml", "gradle", "xml", "txt", "csv", "sh", "py", "js", "ts", "tsx", "css", "html", "gitignore", "properties" -> "text/plain"
        else -> "application/octet-stream"
    }
}

@Preview(showBackground = true, locale = "en")
@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun HomePreview() { AGitTheme { HomeScreen(Catalog(), {}, {}, {}, {}) } }

@Preview(showBackground = true)
@Composable
private fun RepositoryPreview() {
    AGitTheme { RepositoryScreen("AGit", RepositoryUiState(false, snapshot = RepositorySnapshot(branch = "main", ahead = 2, behind = 0)), false, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
