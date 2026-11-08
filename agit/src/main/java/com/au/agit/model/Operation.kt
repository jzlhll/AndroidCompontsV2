package com.au.agit.model

enum class StepStatus { Pending, Running, Success, Failed, Skipped, Cancelled }
enum class OperationStatus { Running, Success, Failed, Blocked, Cancelled }

data class OperationStep(
    val command: String,
    val status: StepStatus = StepStatus.Pending,
    val result: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val receivedBytes: Long? = null,
)

data class OperationState(
    val projectId: String?,
    val title: String,
    val steps: List<OperationStep>,
    val status: OperationStatus = OperationStatus.Running,
    val result: String? = null,
    val cancellable: Boolean = false,
    val cancelling: Boolean = false,
    val allowTemporaryBranch: Boolean = false,
    val preview: PushPlan? = null,
    val kind: String = "",
)

interface OperationReporter {
    suspend fun start(index: Int, command: String, cancellable: Boolean = false)
    suspend fun progress(index: Int, receivedBytes: Long)
    suspend fun complete(index: Int, result: String)
    suspend fun fail(index: Int, result: String)
    suspend fun skip(index: Int, result: String)
}
