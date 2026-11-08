package com.au.agit.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

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
