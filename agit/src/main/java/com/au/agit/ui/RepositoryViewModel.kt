package com.au.agit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.agit.R
import com.au.agit.appString
import com.au.agit.data.AGitRepository
import com.au.agit.data.safeFailure
import com.au.agit.data.UserFeedback
import com.au.agit.model.*
import com.au.module_android.log.logd
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RepositoryUiState(
    val loading: Boolean = true,
    val directory: String = "",
    val snapshot: RepositorySnapshot? = null,
    val files: List<RepositoryFile> = emptyList(),
    val candidates: List<EditCandidate> = emptyList(),
    val fingerprints: Map<String, String> = emptyMap(),
    val error: String? = null,
)

/** 每个 NavEntry 独立持有仓库页面状态，刷新取消并等待前一任务结束。 */
class RepositoryViewModel(
    val projectId: String,
    private val repository: AGitRepository,
    private val feedback: UserFeedback,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RepositoryUiState())
    val stateFlow = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    fun cancelRefresh() { refreshJob?.cancel() }

    fun refresh(directory: String = mutableState.value.directory) {
        val previous = refreshJob
        previous?.cancel()
        refreshJob = viewModelScope.launch {
            previous?.join()
            mutableState.value = mutableState.value.copy(loading = true, directory = directory, error = null)
            try {
                val project = repository.store.catalogFlow.first().projects.firstOrNull { it.id == projectId }
                    ?: throw AGitException(appString(R.string.missing_project), "MISSING_PROJECT")
                val snapshot = if (project.hasGit) repository.snapshot(projectId) else null
                val files = repository.files(projectId, directory).toMutableList()
                val prefix = if (directory.isEmpty()) "" else "$directory/"
                snapshot?.changes.orEmpty().flatMap { it.paths }.filter { it.startsWith(prefix) }.forEach { path ->
                    val remaining = path.removePrefix(prefix)
                    val name = remaining.substringBefore('/')
                    val entryPath = prefix + name
                    if (name.isNotEmpty() && files.none { it.path == entryPath }) {
                        files.add(RepositoryFile(entryPath, name, remaining.contains('/'), 0, exists = false))
                    }
                }
                files.sortWith(compareByDescending<RepositoryFile> { it.directory }.thenBy { it.name.lowercase() })
                val candidates = if (project.hasGit) repository.candidates(projectId) else emptyList()
                val fingerprints = if (project.hasGit) repository.fingerprints(projectId, snapshot?.changes.orEmpty().flatMap { it.paths }.toSet()) else emptyMap()
                ensureActive()
                mutableState.value = RepositoryUiState(false, directory, snapshot, files, candidates, fingerprints)
                logd { "repository scan completed project=$projectId hasGit=${project.hasGit} changes=${snapshot?.changes?.size ?: 0} ahead=${snapshot?.ahead} behind=${snapshot?.behind}" }
            } catch (error: CancellationException) { throw error } catch (error: Exception) { failed(error) } catch (error: LinkageError) { failed(error) }
        }
    }

    private fun failed(error: Throwable) {
        mutableState.value = mutableState.value.copy(loading = false, error = safeFailure(error).message)
        feedback.failure("repository scan", error, projectId)
    }
}
