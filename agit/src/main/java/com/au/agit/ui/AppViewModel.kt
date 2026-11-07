package com.au.agit.ui

import com.au.agit.R
import com.au.agit.appString
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.au.agit.data.AGitRepository
import com.au.agit.data.safeFailure
import com.au.agit.data.UserFeedback
import com.au.agit.model.*
import com.au.module_android.log.logd
import com.au.module_android.log.loge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class SavedForm(val id: String = UUID.randomUUID().toString(), val kind: String, val groupId: String? = null, val savedGroupId: String? = null)

private data class TokenGroupDraft(val name: String, val token: CharArray)

/** 应用级操作状态独立于导航，配置变化及重新组合不会重复执行 Git 操作。 */
class AppViewModel(val repository: AGitRepository, private val feedback: UserFeedback) : ViewModel() {
    private val mutableCatalog = MutableStateFlow<Catalog?>(null)
    val catalogFlow = mutableCatalog.asStateFlow()
    private val mutableLoadError = MutableStateFlow<String?>(null)
    val loadErrorFlow = mutableLoadError.asStateFlow()
    private val mutableOperation = MutableStateFlow<OperationState?>(null)
    val operationFlow = mutableOperation.asStateFlow()
    val noticeFlow = feedback.noticeFlow
    private val mutableActionBusy = MutableStateFlow(false)
    val actionBusyFlow = mutableActionBusy.asStateFlow()
    private val mutableSavedForm = MutableStateFlow<SavedForm?>(null)
    val savedFormFlow = mutableSavedForm.asStateFlow()
    private var operationJob: Job? = null
    private var catalogJob: Job? = null
    private var tokenGroupDraft: TokenGroupDraft? = null

    init { loadCatalog() }

    fun loadCatalog() {
        if (catalogJob?.isActive == true) return
        mutableLoadError.value = null
        catalogJob = viewModelScope.launch {
            try { repository.store.catalogFlow.collect { mutableCatalog.value = it } } catch (error: CancellationException) { throw error } catch (error: Exception) { mutableLoadError.value = safeFailure(error).message; failure("catalog load", error) }
        }
    }

    fun notify(message: String) { feedback.notify(message) }
    fun acknowledgeNotice(id: String) { feedback.acknowledge(id) }

    fun saveGroup(name: String, token: String, id: String?) = quiet("credential save") {
        val savedId = repository.saveGroup(name, token, id)
        notify(appString(R.string.group_saved))
        mutableSavedForm.value = SavedForm(kind = "token", groupId = id, savedGroupId = savedId)
    }

    fun prepareTokenGroup(name: String, token: String): Boolean {
        if (name.isBlank() || token.isBlank() || token.any { it.isWhitespace() }) {
            notify(appString(R.string.invalid_token))
            return false
        }
        clearTokenGroupDraft()
        tokenGroupDraft = TokenGroupDraft(name.trim(), token.toCharArray())
        return true
    }

    fun tokenGroupInput(): Pair<String, String>? = tokenGroupDraft?.let { it.name to it.token.concatToString() }

    fun clearTokenGroupDraft() {
        tokenGroupDraft?.token?.fill('\u0000')
        tokenGroupDraft = null
    }

    fun saveTokenGroupWithAuthor(name: String, email: String) = quiet("credential and author save") {
        val draft = tokenGroupDraft ?: throw AGitException(appString(R.string.token_reenter), "TOKEN_DRAFT_MISSING")
        val savedId = repository.saveGroup(draft.name, draft.token.concatToString(), author = Author(name.trim(), email.trim()))
        clearTokenGroupDraft()
        notify(appString(R.string.group_author_saved))
        mutableSavedForm.value = SavedForm(kind = "token_author", savedGroupId = savedId)
    }

    fun saveAuthor(name: String, email: String) = quiet("author save") {
        repository.saveAuthor(name, email)
        notify(appString(R.string.author_saved))
        mutableSavedForm.value = SavedForm(kind = "author")
    }

    fun acknowledgeSavedForm(id: String) { if (mutableSavedForm.value?.id == id) mutableSavedForm.value = null }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            try {
                repository.store.update { it.copy(theme = theme) }
                logd { "theme selected theme=$theme" }
            } catch (error: CancellationException) { throw error } catch (error: Exception) { failure("theme save", error) }
        }
    }

    fun select(projectId: String) {
        viewModelScope.launch {
            try { repository.select(projectId); logd { "project selected project=$projectId" } } catch (error: CancellationException) { throw error } catch (error: Exception) { failure("project select", error, projectId) }
        }
    }
    fun finishEditing(projectId: String) = quiet("external edit finish", projectId) {
        repository.finishEditing(projectId)
        notify(appString(R.string.edit_finished))
    }
    fun resolveCandidate(candidate: EditCandidate, overwrite: Boolean) = quiet("edit copy resolve", candidate.projectId) {
        repository.resolveCandidate(candidate, overwrite)
        notify(if (overwrite) appString(R.string.copy_replaced) else appString(R.string.both_kept))
    }

    fun clone(groupId: String, url: String) = operation("clone", null, appString(R.string.clone_title), listOf("git clone")) { reporter ->
        repository.clone(groupId, url, reporter)
        appString(R.string.project_added)
    }

    fun commit(projectId: String, selected: Set<String>, expected: Map<String, String>, message: String) =
        operation("commit", projectId, appString(R.string.commit_title), listOf("git status --short", "git commit --only")) { reporter ->
            repository.commit(projectId, selected, expected, message, reporter)
            appString(R.string.commit_done)
        }

    fun update(projectId: String) = operation("update", projectId, appString(R.string.update_title), listOf("git fetch origin", appString(R.string.check_state), "git merge --ff-only")) { reporter ->
        repository.update(projectId, reporter)
        appString(R.string.update_done)
    }

    fun preparePush(projectId: String) = operation("push check", projectId, appString(R.string.push_check_title), listOf("git fetch origin", appString(R.string.check_state), "git push origin")) { reporter ->
        val plan = repository.preparePush(projectId, reporter)
        mutableOperation.value = mutableOperation.value?.copy(
            status = if (plan.blocked) OperationStatus.Blocked else OperationStatus.Success,
            preview = plan,
            allowTemporaryBranch = plan.blocked,
            result = if (plan.blocked) appString(R.string.remote_ahead) else null,
            steps = mutableOperation.value!!.steps.map { if (it.status == StepStatus.Pending) it.copy(status = StepStatus.Skipped) else it },
        )
        if (plan.blocked) notify(appString(R.string.remote_ahead))
        null
    }

    fun continuePush(temporary: Boolean) {
        val state = mutableOperation.value ?: return
        val plan = state.preview ?: return
        if (state.status == OperationStatus.Running || operationJob?.isActive == true) return
        val offset = state.steps.size
        val next = listOf("git fetch origin", appString(R.string.check_state)) + if (temporary) listOf(appString(R.string.create_branch), "git push origin HEAD:<temporary>") else listOf("git push origin HEAD:${plan.target}")
        operation("push", plan.projectId, if (temporary) appString(R.string.temporary_push) else appString(R.string.push_title), next, previous = state) { reporter ->
            repository.push(plan, temporary, reporter, offset)
            appString(if (temporary) R.string.push_done_temporary else R.string.push_done)
        }
    }

    fun cancelOperation() {
        val state = mutableOperation.value ?: return
        if (state.status != OperationStatus.Running || !state.cancellable || state.cancelling) return
        mutableOperation.value = state.copy(cancelling = true, cancellable = false)
        operationJob?.cancel()
    }

    fun closeOperation() { if (mutableOperation.value?.status != OperationStatus.Running) mutableOperation.value = null }

    private fun operation(
        kind: String,
        projectId: String?,
        title: String,
        commands: List<String>,
        previous: OperationState? = null,
        block: suspend (OperationReporter) -> String?,
    ) {
        if (operationJob?.isActive == true || mutableOperation.value?.status == OperationStatus.Running) return
        val traceId = UUID.randomUUID().toString().take(8)
        val startedAt = SystemClock.elapsedRealtime()
        mutableOperation.value = OperationState(projectId, title, (previous?.steps.orEmpty()) + commands.map { OperationStep(it) }, kind = kind)
        logd { "operation started trace=$traceId kind=$kind project=${projectId ?: "new"}" }
        val reporter = object : OperationReporter {
            override suspend fun start(index: Int, command: String, cancellable: Boolean) = withContext(Dispatchers.Main.immediate) {
                mutableOperation.value = mutableOperation.value?.let { state ->
                    val steps = if (index == state.steps.size) state.steps + OperationStep(command) else state.steps
                    state.copy(cancellable = cancellable, steps = steps.mapIndexed { i, step ->
                        if (i == index) step.copy(command = command, status = StepStatus.Running, startedAt = SystemClock.elapsedRealtime()) else step
                    })
                }
                logd { "operation step started trace=$traceId kind=$kind project=${projectId ?: "new"} step=$index command=$command" }
            }
            override suspend fun complete(index: Int, result: String) = withContext(Dispatchers.Main.immediate) {
                mutableOperation.value = mutableOperation.value?.let { state ->
                    state.copy(cancellable = false, steps = state.steps.mapIndexed { i, step ->
                        if (i == index) step.copy(status = StepStatus.Success, result = result, finishedAt = SystemClock.elapsedRealtime()) else step
                    })
                }
                logd { "operation step completed trace=$traceId kind=$kind project=${projectId ?: "new"} step=$index" }
            }
            override suspend fun fail(index: Int, result: String) = withContext(Dispatchers.Main.immediate) {
                mutableOperation.value = mutableOperation.value?.let { state -> state.copy(cancellable = false,
                    steps = state.steps.mapIndexed { i, step -> if (i == index) step.copy(status = StepStatus.Failed, result = result, finishedAt = SystemClock.elapsedRealtime()) else step }) }
                loge { "operation step failed trace=$traceId kind=$kind project=${projectId ?: "new"} step=$index" }
            }
            override suspend fun skip(index: Int, result: String) = withContext(Dispatchers.Main.immediate) {
                mutableOperation.value = mutableOperation.value?.let { state -> state.copy(cancellable = false,
                    steps = state.steps.mapIndexed { i, step -> if (i == index) step.copy(status = StepStatus.Skipped, result = result) else step }) }
                logd { "operation step skipped trace=$traceId kind=$kind project=${projectId ?: "new"} step=$index" }
            }
        }
        operationJob = viewModelScope.launch {
            try {
                val result = block(reporter)
                if (result != null) mutableOperation.value = mutableOperation.value?.copy(status = OperationStatus.Success, result = result, cancellable = false)
                logd { "operation finished trace=$traceId kind=$kind project=${projectId ?: "new"} status=${mutableOperation.value?.status} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}" }
                if (result != null) notify(result)
            } catch (error: CancellationException) {
                // withContext(IO) 返回后才宣告取消，确保引擎已经离开当前执行阶段。
                finishFailure(OperationStatus.Cancelled, appString(R.string.cancelled), StepStatus.Cancelled)
                logd { "operation cancelled trace=$traceId kind=$kind project=${projectId ?: "new"}" }
            } catch (error: PushBlockedException) {
                finishFailure(OperationStatus.Blocked, error.message.orEmpty(), StepStatus.Failed)
                mutableOperation.value = mutableOperation.value?.copy(preview = error.plan, allowTemporaryBranch = true)
                failure(kind, error, projectId, traceId)
            } catch (error: Exception) {
                if (!isActive) {
                    finishFailure(OperationStatus.Cancelled, appString(R.string.cancelled), StepStatus.Cancelled)
                    logd { "operation cancelled trace=$traceId kind=$kind project=${projectId ?: "new"}" }
                } else {
                    finishFailure(OperationStatus.Failed, safeFailure(error).message, StepStatus.Failed)
                    failure(kind, error, projectId, traceId)
                }
            } catch (error: LinkageError) {
                finishFailure(OperationStatus.Failed, safeFailure(error).message, StepStatus.Failed)
                failure(kind, error, projectId, traceId)
            } finally {
                if (projectId != null) repository.changesFlow.tryEmit(projectId)
            }
        }
    }

    private fun finishFailure(status: OperationStatus, message: String, stepStatus: StepStatus) {
        mutableOperation.value = mutableOperation.value?.let { state -> state.copy(
            status = status, result = message, cancellable = false, cancelling = false,
            steps = state.steps.map { when (it.status) {
                StepStatus.Running -> it.copy(status = stepStatus, result = message, finishedAt = SystemClock.elapsedRealtime())
                StepStatus.Pending -> it.copy(status = StepStatus.Skipped)
                else -> it
            } },
        ) }
    }

    fun quiet(kind: String, projectId: String? = null, block: suspend () -> Unit) {
        if (mutableActionBusy.value) return
        mutableActionBusy.value = true
        viewModelScope.launch {
            val traceId = UUID.randomUUID().toString().take(8)
            logd { "action started trace=$traceId kind=$kind project=${projectId ?: "none"}" }
            try { block(); logd { "action completed trace=$traceId kind=$kind project=${projectId ?: "none"}" } } catch (error: CancellationException) { throw error } catch (error: Exception) { failure(kind, error, projectId, traceId) } catch (error: LinkageError) { failure(kind, error, projectId, traceId) } finally { mutableActionBusy.value = false }
        }
    }

    fun failure(kind: String, error: Throwable, projectId: String? = null, traceId: String = "none") {
        feedback.failure(kind, error, projectId, traceId)
    }

    override fun onCleared() {
        clearTokenGroupDraft()
        super.onCleared()
    }
}
