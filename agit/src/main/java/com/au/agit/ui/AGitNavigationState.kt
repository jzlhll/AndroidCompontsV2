package com.au.agit.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.au.agit.model.Catalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 持有应用导航栈、抽屉及分组选择状态，并协调选择持久化和表单完成后的导航。 */
class AGitNavigationState(
    val backStack: NavBackStack<NavKey>,
    val drawer: DrawerState,
    private val model: AppViewModel,
    private val scope: CoroutineScope,
    choosingTokenGroupState: MutableState<Boolean>,
) {
    var choosingTokenGroup by choosingTokenGroupState

    fun back() {
        if (backStack.size > 1) {
            val key = backStack.lastOrNull()
            if (key is TokenKey && key.groupId == null) model.clearTokenGroupDraft()
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun openProject(id: String) {
        scope.launch {
            if (!model.select(id).await()) return@launch
            backStack.clear()
            backStack.add(RepositoryKey(id))
            drawer.close()
        }
    }

    fun openGroup(id: String) {
        scope.launch {
            if (!model.selectGroup(id).await()) return@launch
            val project = model.catalogFlow.value?.currentProject
            backStack.clear()
            backStack.add(if (project == null) HomeKey else RepositoryKey(project.id))
            drawer.close()
        }
    }

    fun go(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    fun openDrawer() {
        scope.launch { drawer.open() }
    }

    fun openFromDrawer(key: NavKey) {
        scope.launch { drawer.close() }
        go(key)
    }

    fun addProject() {
        scope.launch { drawer.close() }
        if (model.catalogFlow.value?.groups.isNullOrEmpty()) go(TokenKey()) else choosingTokenGroup = true
    }

    fun cloneInGroup(groupId: String) {
        if ((backStack.lastOrNull() as? CloneKey)?.groupId != groupId) go(CloneKey(groupId))
    }

    suspend fun openCompletedClone(key: CloneKey) {
        if (backStack.lastOrNull() != key) return
        if (model.catalogFlow.value?.currentProject?.id != key.projectId && !model.select(key.projectId).await()) return
        if (backStack.lastOrNull() != key) return
        backStack.clear()
        backStack.add(RepositoryKey(key.projectId))
    }

    fun pruneRemovedEntries(catalog: Catalog) {
        val groups = catalog.groups.map { it.id }.toSet()
        val projects = catalog.projects.map { it.id }.toSet()
        backStack.removeAll { entry ->
            when (entry) {
                is TokenKey -> entry.groupId != null && entry.groupId !in groups
                is CloneKey -> (entry.groupId != null && entry.groupId !in groups) || entry.projectId in catalog.pendingDeletionProjectIds
                is RepositoryKey -> entry.projectId !in projects
                is CommitKey -> entry.projectId !in projects
                is MediaKey -> entry.projectId !in projects
                is DiffKey -> entry.projectId !in projects
                else -> false
            }
        }
        val first = backStack.firstOrNull()
        if (first != HomeKey && first !is RepositoryKey) {
            backStack.add(0, catalog.currentProject?.let { RepositoryKey(it.id) } ?: HomeKey)
        }
    }

    fun onFormSaved(saved: SavedForm) {
        val key = backStack.lastOrNull()
        if (saved.kind == "author" && key is AuthorKey) back()
        if (saved.kind == "token_author" && key is AuthorKey && key.tokenGroupSetup) {
            back()
            if (backStack.lastOrNull() is TokenKey) back()
            if (key.cloneAfterSave && saved.savedGroupId != null) cloneInGroup(saved.savedGroupId)
        }
        if (saved.kind == "token" && key is TokenKey && key.groupId == saved.groupId) {
            back()
            if (key.cloneAfterSave && saved.savedGroupId != null) cloneInGroup(saved.savedGroupId)
        }
        model.acknowledgeSavedForm(saved.id)
    }
}

@Composable
fun rememberAGitNavigationState(model: AppViewModel, catalog: Catalog?, savedForm: SavedForm?, hasOperation: Boolean): AGitNavigationState {
    val backStack = rememberNavBackStack(HomeKey)
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val choosingTokenGroup = rememberSaveable { mutableStateOf(false) }
    val navigation = remember(model, backStack, drawer, scope, choosingTokenGroup) {
        AGitNavigationState(backStack, drawer, model, scope, choosingTokenGroup)
    }
    val readySavedForm = savedForm?.takeIf { saved ->
        saved.savedGroupId == null || catalog?.groups?.any { it.id == saved.savedGroupId } == true
    }
    LaunchedEffect(readySavedForm?.id) {
        readySavedForm?.let(navigation::onFormSaved)
    }
    LaunchedEffect(catalog?.groups, catalog?.projects, catalog?.pendingDeletionProjectIds, backStack.toList()) {
        catalog?.let(navigation::pruneRemovedEntries)
    }
    val cloneKey = backStack.lastOrNull() as? CloneKey
    val completedClone = cloneKey?.takeIf { key -> catalog?.projects?.any { it.id == key.projectId && it.groupId == key.groupId } == true }
    LaunchedEffect(completedClone, hasOperation) {
        if (!hasOperation && completedClone != null) navigation.openCompletedClone(completedClone)
    }
    LaunchedEffect(catalog?.currentProject?.id, backStack.singleOrNull()) {
        if (backStack.singleOrNull() == HomeKey) {
            catalog?.currentProject?.let { backStack[0] = RepositoryKey(it.id) }
        }
    }
    return navigation
}
