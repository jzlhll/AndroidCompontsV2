package com.au.agit.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenGroup(val id: String, val name: String, val encryptedToken: String)

@Serializable
data class GitProject(val id: String, val groupId: String, val name: String, val url: String)

@Serializable
data class Author(val name: String = "", val email: String = "") {
    val isConfigured: Boolean get() = email.trim().matches(Regex("[^\\s@<>]+@[^\\s@<>]+"))
    val displayName: String get() = name.trim().ifBlank { email.trim() }
    val commitName: String get() = name.trim().ifBlank { email.trim().substringBefore('@') }
}

@Serializable
data class EditSession(
    val projectId: String,
    val path: String,
    val existingPaths: Set<String>,
    val active: Boolean = true,
    val dismissed: Set<String> = emptySet(),
)

@Serializable
data class Catalog(
    val groups: List<TokenGroup> = emptyList(),
    val projects: List<GitProject> = emptyList(),
    val author: Author = Author(),
    val selectedProjectId: String? = null,
    val edits: List<EditSession> = emptyList(),
    val pendingBranches: Map<String, String> = emptyMap(),
    val theme: AppTheme = AppTheme.White,
)

data class FileChange(
    val path: String,
    val kind: String,
    val paths: Set<String> = setOf(path),
    val staged: Boolean = false,
    val type: ChangeType = ChangeType.Modified,
)

enum class ChangeType(val marker: String) { Added("A"), Modified("M"), Deleted("D"), Renamed("R"), Conflicted("U") }

data class CommitSummary(val id: String, val message: String)

data class PushOutcome(val accepted: Boolean, val nonFastForward: Boolean, val message: String)

data class RepositorySnapshot(
    val branch: String = "",
    val target: String? = null,
    val head: String? = null,
    val changes: List<FileChange> = emptyList(),
    val ahead: Int? = null,
    val behind: Int? = null,
    val commits: List<CommitSummary> = emptyList(),
    val problem: String? = null,
    val fetched: Boolean = false,
) {
    val dirty: Boolean get() = changes.isNotEmpty()
}

data class RepositoryFile(val path: String, val name: String, val directory: Boolean, val size: Long, val exists: Boolean = true)

data class EditCandidate(
    val projectId: String,
    val original: String,
    val candidate: String,
    val originalHash: String,
    val candidateHash: String,
) {
    val signature: String get() = "$candidate:$candidateHash"
}

data class PushPlan(
    val projectId: String,
    val projectName: String,
    val branch: String,
    val target: String,
    val head: String,
    val commits: List<CommitSummary>,
    val dirty: Boolean,
    val blocked: Boolean,
)

open class AGitException(message: String, val code: String = "PRECONDITION", cause: Throwable? = null) : Exception(message, cause)

class PushBlockedException(val plan: PushPlan, message: String) : AGitException(message, "REMOTE_AHEAD")
