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

    fun onFormSaved(saved: SavedForm) {
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
        if (saved.kind == "token_deleted") {
            backStack.removeAll { entry ->
                when (entry) {
                    is TokenKey -> entry.groupId == saved.groupId
                    is CloneKey -> entry.groupId == saved.groupId
                    is RepositoryKey -> entry.projectId in saved.removedProjectIds
                    is CommitKey -> entry.projectId in saved.removedProjectIds
                    is MediaKey -> entry.projectId in saved.removedProjectIds
                    else -> false
                }
            }
            if (backStack.isEmpty()) backStack.add(HomeKey)
        }
        model.acknowledgeSavedForm(saved.id)
    }
}

@Composable
fun rememberAGitNavigationState(model: AppViewModel, catalog: Catalog?, savedForm: SavedForm?): AGitNavigationState {
    val backStack = rememberNavBackStack(HomeKey)
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val choosingTokenGroup = rememberSaveable { mutableStateOf(false) }
    val navigation = remember(model, backStack, drawer, scope, choosingTokenGroup) {
        AGitNavigationState(backStack, drawer, model, scope, choosingTokenGroup)
    }
    LaunchedEffect(savedForm?.id) {
        savedForm?.let(navigation::onFormSaved)
    }
    LaunchedEffect(catalog?.currentProject?.id, backStack.singleOrNull()) {
        if (backStack.singleOrNull() == HomeKey) {
            catalog?.currentProject?.let { backStack[0] = RepositoryKey(it.id) }
        }
    }
    return navigation
}
