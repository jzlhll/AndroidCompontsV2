package com.au.agit.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import com.au.agit.model.Catalog
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val PageSlideAnimationSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

@Composable
fun AGitNavHost(
    model: AppViewModel,
    catalog: Catalog,
    navigation: AGitNavigationState,
    actionBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        NavDisplay(
            backStack = navigation.backStack,
            onBack = { navigation.back() },
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
                    HomeScreen(catalog, onMenu = navigation::openDrawer,
                        onAddToken = { navigation.go(TokenKey()) },
                        onClone = { catalog.currentGroup?.let { navigation.go(CloneKey(it.id)) } ?: navigation.addProject() },
                        onProject = navigation::openProject)
                }
                entry<RepositoryKey> { key ->
                    val repositoryModel = koinViewModel<RepositoryViewModel>(parameters = { parametersOf(key.projectId) })
                    RepositoryRoute(key, catalog, repositoryModel, model,
                        onBack = navigation::back, onMenu = navigation::openDrawer,
                        onDirectory = { navigation.go(RepositoryKey(key.projectId, it)) },
                        onCommit = { navigation.go(CommitKey(key.projectId)) }, onMedia = { navigation.go(MediaKey(key.projectId, it)) })
                }
                entry<CommitKey> { key ->
                    val repositoryModel = koinViewModel<RepositoryViewModel>(parameters = { parametersOf(key.projectId) })
                    CommitRoute(repositoryModel, model, catalog.author, onBack = navigation::back, onAuthor = { navigation.go(AuthorKey()) })
                }
                entry<TokenKey> { key ->
                    TokenRoute(key, catalog, model, actionBusy, onBack = navigation::back,
                        onAuthorSetup = { navigation.go(AuthorKey(tokenGroupSetup = true, cloneAfterSave = key.cloneAfterSave)) })
                }
                entry<CloneKey> { key ->
                    if (key.groupId == null) {
                        LaunchedEffect(key) { navigation.back(); navigation.choosingTokenGroup = true }
                    } else CloneScreen(catalog.groups.firstOrNull { it.id == key.groupId }, actionBusy, onBack = navigation::back, onClone = model::clone)
                }
                entry<AuthorKey> { key ->
                    val isCurrent = navigation.backStack.lastOrNull() == key
                    LaunchedEffect(key, isCurrent) {
                        // 退场页面仍参与动画组合；只在实际栈顶检查恢复所需的草稿。
                        if (!key.tokenGroupSetup || !isCurrent || navigation.backStack.lastOrNull() != key) return@LaunchedEffect
                        if (model.hasTokenGroupDraft() || model.savedFormFlow.value?.kind == "token_author") return@LaunchedEffect
                        model.failure("credential setup restore", AGitException(appString(R.string.token_reenter), "TOKEN_DRAFT_MISSING"))
                        navigation.back()
                    }
                    AuthorScreen(catalog.author, actionBusy, onBack = navigation::back,
                        onSave = if (key.tokenGroupSetup) model::saveTokenGroupWithAuthor else model::saveAuthor)
                }
                entry<LogsKey> {
                    val logsModel = koinViewModel<LogsViewModel>()
                    LogsRoute(logsModel, model, onBack = navigation::back, onOpen = { navigation.go(LogFileKey(it)) })
                }
                entry<SettingsKey> { SettingsScreen(onBack = navigation::back, onLogs = { navigation.go(LogsKey) }) }
                entry<LogFileKey> { key ->
                    val logsModel = koinViewModel<LogsViewModel>(parameters = { parametersOf(key.name) })
                    LogFileRoute(key.name, logsModel, onBack = navigation::back)
                }
                entry<ThemeKey> { ThemeScreen(catalog.theme, model::setTheme, onBack = navigation::back) }
                entry<MediaKey> { key -> MediaRoute(key, model, onBack = navigation::back) }
            },
        )
    }
    val selected = (navigation.backStack.lastOrNull() as? RepositoryKey)?.projectId ?: catalog.currentProject?.id
    val drawerContent: @Composable () -> Unit = {
        ProjectDrawer(catalog, selected,
            onSelect = navigation::openProject, onSelectGroup = navigation::openGroup, onClone = navigation::addProject,
            onToken = { id -> navigation.openFromDrawer(TokenKey(id)) },
            onAuthor = { navigation.openFromDrawer(AuthorKey()) }, onSettings = { navigation.openFromDrawer(SettingsKey) },
            onTheme = { navigation.openFromDrawer(ThemeKey) })
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { drawerContent() } }) { content() }
        } else {
            ModalNavigationDrawer(drawerState = navigation.drawer, drawerContent = { ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) { drawerContent() } }) { content() }
        }
    }
}
