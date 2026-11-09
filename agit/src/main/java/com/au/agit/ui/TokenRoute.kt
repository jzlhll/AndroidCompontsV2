package com.au.agit.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.au.agit.model.Catalog
import kotlinx.coroutines.CancellationException

@Composable
fun TokenRoute(
    key: TokenKey,
    catalog: Catalog,
    model: AppViewModel,
    busy: Boolean,
    onBack: () -> Unit,
    onAuthorSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val needsAuthor = key.groupId == null && !catalog.author.isConfigured
    var initialToken by remember(key.groupId) { mutableStateOf("") }
    var loadingToken by remember(key.groupId) { mutableStateOf(key.groupId != null) }
    LaunchedEffect(key.groupId) {
        val id = key.groupId ?: return@LaunchedEffect
        try {
            initialToken = model.repository.groupToken(id)
        } catch (error: CancellationException) { throw error } catch (error: Exception) {
            model.failure("credential load", error)
        } finally {
            loadingToken = false
        }
    }
    TokenScreen(catalog.groups.firstOrNull { it.id == key.groupId }, busy || loadingToken, needsAuthor, onBack = onBack,
        onSave = { name, token ->
            if (needsAuthor) {
                if (model.prepareTokenGroup(name, token)) onAuthorSetup()
            } else model.saveGroup(name, token, key.groupId)
        }, onDelete = { key.groupId?.let(model::deleteGroup) }, modifier = modifier, initialToken = initialToken,
        draft = if (key.groupId == null) model.tokenGroupInput() else null,
        projectNames = catalog.projects.filter { it.groupId == key.groupId }.map { it.name })
}
