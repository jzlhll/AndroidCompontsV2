package com.au.agit.data

import com.au.agit.R
import com.au.agit.appString
import android.content.Context
import android.content.Intent
import com.au.agit.git.JGitEngine
import com.au.agit.git.GitHubArchiveDownloader
import com.au.agit.git.RepositoryPaths
import com.au.agit.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.jgit.api.Git
import com.au.module_android.utilsmedia.getUriFromFile
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** 协调凭据、项目隔离与 Git 事务；同一项目的扫描、写操作和回写共用一把锁。 */
class AGitRepository(
    private val context: Context,
    val store: CatalogStore,
    private val vault: TokenVault,
    private val engine: JGitEngine,
) {
    val paths = RepositoryPaths(File(context.filesDir, "shared"))
    val changesFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val credentialLock = Mutex()
    private val cloneTempDirectory = File(context.filesDir, "temp/clones")

    suspend fun saveGroup(name: String, token: String, existingId: String? = null, author: Author? = null) = withContext(Dispatchers.IO) {
        if (name.isBlank() || token.isBlank() || token.any { it.isWhitespace() }) throw AGitException(appString(R.string.invalid_token), "INVALID_TOKEN")
        if (author != null && !author.isConfigured) throw AGitException(appString(R.string.invalid_author), "INVALID_AUTHOR")
        credentialLock.withLock {
            val id = existingId ?: UUID.randomUUID().toString()
            val group = TokenGroup(id, name.trim(), vault.encrypt(id, token.trim()))
            store.update { catalog ->
                if (existingId != null && catalog.groups.none { it.id == existingId }) throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
                catalog.copy(
                    groups = catalog.groups.filterNot { it.id == id } + group,
                    author = author ?: catalog.author,
                    selectedGroupId = catalog.currentGroup?.id ?: id,
                    selectedProjectId = catalog.currentProject?.id,
                )
            }
            id
        }
    }

    suspend fun groupToken(groupId: String): String = withContext(Dispatchers.IO) {
        credentialLock.withLock {
            val token = tokenForGroup(groupId)
            try { token.concatToString() } finally { token.fill('\u0000') }
        }
    }

    suspend fun deleteGroup(groupId: String): Set<String> = withContext(Dispatchers.IO) {
        credentialLock.withLock {
            val catalog = store.catalogFlow.first()
            if (catalog.groups.none { it.id == groupId }) throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
            val projectIds = catalog.projects.filter { it.groupId == groupId }.map { it.id }.sorted().toSet()
            val acquiredLocks = mutableListOf<Mutex>()
            try {
                projectIds.forEach { id ->
                    val lock = locks.getOrPut(id) { Mutex() }
                    lock.lock()
                    acquiredLocks += lock
                }
                projectIds.forEach { requireEditingFinished(it) }
                currentCoroutineContext().ensureActive()
                // 开始删除后完成索引清理；不跟随符号链接，不触及分组外的仓库或远端。
                withContext(NonCancellable) {
                    projectIds.forEach { id ->
                        val root = paths.root(id).toPath()
                        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                            Files.walk(root).use { stream ->
                                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                            }
                        }
                    }
                    store.update { current ->
                        val remainingGroups = current.groups.filterNot { it.id == groupId }
                        val remainingProjects = current.projects.filterNot { it.id in projectIds }
                        val selectedGroup = remainingGroups.firstOrNull { it.id == current.currentGroup?.id } ?: remainingGroups.firstOrNull()
                        val selectedProject = remainingProjects.firstOrNull { it.id == current.currentProject?.id && it.groupId == selectedGroup?.id }
                            ?: remainingProjects.firstOrNull { it.groupId == selectedGroup?.id }
                        current.copy(
                            groups = remainingGroups,
                            projects = remainingProjects,
                            selectedGroupId = selectedGroup?.id,
                            selectedProjectId = selectedProject?.id,
                            edits = current.edits.filterNot { it.projectId in projectIds },
                            pendingBranches = current.pendingBranches - projectIds,
                        )
                    }
                }
            } finally {
                acquiredLocks.asReversed().forEach { it.unlock() }
            }
            projectIds
        }
    }

    suspend fun saveAuthor(name: String, email: String) {
        val author = Author(name.trim(), email.trim())
        if (!author.isConfigured) throw AGitException(appString(R.string.invalid_author), "INVALID_AUTHOR")
        store.update { it.copy(author = author) }
    }

    suspend fun select(projectId: String) {
        store.update { catalog ->
            val project = catalog.projects.firstOrNull { it.id == projectId } ?: throw AGitException(appString(R.string.missing_project), "MISSING_PROJECT")
            if (catalog.groups.none { it.id == project.groupId }) throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
            catalog.copy(selectedGroupId = project.groupId, selectedProjectId = projectId)
        }
    }

    suspend fun selectGroup(groupId: String) {
        store.update { catalog ->
            if (catalog.groups.none { it.id == groupId }) throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
            val project = catalog.currentProject?.takeIf { it.groupId == groupId } ?: catalog.projects.firstOrNull { it.groupId == groupId }
            catalog.copy(selectedGroupId = groupId, selectedProjectId = project?.id)
        }
    }

    suspend fun clone(groupId: String, input: String, mode: CloneMode, depth: Int, reporter: OperationReporter): GitProject = withContext(Dispatchers.IO) {
        if (mode == CloneMode.Depth && depth <= 0) throw AGitException(appString(R.string.invalid_depth), "INVALID_DEPTH")
        val url = engine.normalizeUrl(input)
        val project = GitProject(UUID.randomUUID().toString(), groupId, url.substringAfterLast('/').removeSuffix(".git"), url,
            mode, if (mode == CloneMode.Depth) depth else null)
        val command = when (mode) {
            CloneMode.Full -> "git clone $url ${project.id}"
            CloneMode.Depth -> "git clone --depth $depth $url ${project.id}"
            CloneMode.Download -> "download source $url ${project.id}"
        }
        locks.getOrPut(project.id) { Mutex() }.withLock {
            val directory = paths.root(project.id).toPath()
            val temporaryDirectory = File(cloneTempDirectory, project.id).toPath()
            val contentDirectory = temporaryDirectory.resolve("content")
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) || Files.exists(temporaryDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw AGitException(appString(R.string.project_exists), "PROJECT_EXISTS")
            }
            val token = tokenForGroup(groupId)
            val job = currentCoroutineContext().job
            try {
                reporter.start(0, command, cancellable = true)
                val receivedBytesFlow = MutableStateFlow(0L)
                coroutineScope {
                    val progressJob = launch {
                        receivedBytesFlow.collect { bytes ->
                            if (bytes > 0) reporter.progress(0, bytes)
                            delay(250.milliseconds)
                        }
                    }
                    try {
                        if (project.hasGit) {
                            engine.clone(project, token, job, contentDirectory.toFile()) { receivedBytesFlow.value = it }
                        } else {
                            GitHubArchiveDownloader().download(project, token, job, contentDirectory.toFile()) { receivedBytesFlow.value = it }
                        }
                    } finally {
                        progressJob.cancelAndJoin()
                    }
                }
                if (receivedBytesFlow.value > 0) reporter.progress(0, receivedBytesFlow.value)
                currentCoroutineContext().ensureActive()
                reporter.start(0, command, cancellable = false)
                withContext(NonCancellable) {
                    credentialLock.withLock {
                        // 下载、检出或解压完成且文件已关闭，之后才原子改名并登记到正式目录。
                        Files.createDirectories(directory.parent)
                        Files.move(contentDirectory, directory, StandardCopyOption.ATOMIC_MOVE)
                        deleteCloneDirectory(temporaryDirectory)
                        store.update { catalog ->
                            if (catalog.groups.none { it.id == groupId }) throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
                            catalog.copy(projects = catalog.projects + project, selectedGroupId = groupId, selectedProjectId = project.id)
                        }
                    }
                }
                reporter.complete(0, appString(if (project.hasGit) R.string.clone_complete else R.string.download_complete))
                project
            } catch (error: Exception) {
                // 保持项目锁，等待下载和回写退出后清理，取消期间也必须完成删除。
                withContext(NonCancellable) {
                    deleteCloneDirectory(temporaryDirectory)
                    if (store.catalogFlow.first().projects.none { it.id == project.id }) {
                        deleteCloneDirectory(directory)
                    }
                }
                throw error
            } finally { token.fill('\u0000') }
        }
    }

    suspend fun cleanupIncompleteClones() = withContext(Dispatchers.IO) {
        val projectIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val projectIds = listOf(cloneTempDirectory, File(context.filesDir, "shared"))
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .map { it.name }.filter { it.matches(projectIdPattern) }.distinct()
        projectIds.forEach { id ->
            currentCoroutineContext().ensureActive()
            // 与 Clone 复用项目锁；本次进程里仍在写入的目录必须等待其完成，不能提前删除。
            locks.getOrPut(id) { Mutex() }.withLock {
                val registered = store.catalogFlow.first().projects.any { it.id == id }
                deleteCloneDirectory(File(cloneTempDirectory, id).toPath())
                if (!registered) {
                    deleteCloneDirectory(paths.root(id).toPath())
                }
            }
        }
    }

    private fun deleteCloneDirectory(directory: Path) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    suspend fun snapshot(projectId: String): RepositorySnapshot = locked(projectId) { _, git -> snapshotFor(projectId, git) }

    suspend fun files(projectId: String, directory: String): List<RepositoryFile> = lockedProject(projectId) {
        val root = paths.resolve(projectId, directory, allowRoot = true)
        if (!root.exists()) return@lockedProject emptyList()
        if (!root.isDirectory) throw AGitException(appString(R.string.missing_directory), "MISSING_DIRECTORY")
        root.listFiles()?.filter { it.name != ".git" }?.map { file ->
            val path = if (directory.isEmpty()) file.name else "$directory/${file.name}"
            RepositoryFile(path, file.name, !Files.isSymbolicLink(file.toPath()) && file.isDirectory, file.length())
        }?.sortedWith(compareByDescending<RepositoryFile> { it.directory }.thenBy { it.name.lowercase() })
            ?: throw AGitException(appString(R.string.unreadable_directory), "UNREADABLE_DIRECTORY")
    }

    suspend fun file(projectId: String, path: String): File = lockedProject(projectId) {
        paths.resolve(projectId, path).also { if (!it.isFile) throw AGitException(appString(R.string.missing_file), "MISSING_FILE") }
    }

    suspend fun beginEdit(projectId: String, path: String): File = locked(projectId) { _, _ ->
        val file = paths.resolve(projectId, path)
        if (!file.isFile) throw AGitException(appString(R.string.missing_file), "MISSING_FILE")
        requireEditingFinished(projectId)
        val siblings = file.parentFile?.listFiles()?.map { it.name }?.toSet().orEmpty()
        store.update { catalog -> catalog.copy(edits = catalog.edits.filterNot { it.projectId == projectId && it.path == path } + EditSession(projectId, path, siblings)) }
        file
    }

    suspend fun finishEditing(projectId: String) = locked(projectId) { _, _ ->
        store.catalogFlow.first().edits.filter { it.projectId == projectId && it.active }.forEach { edit ->
            getUriFromFile(context, paths.resolve(projectId, edit.path))?.let { uri ->
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        store.update { catalog -> catalog.copy(edits = catalog.edits.map { if (it.projectId == projectId) it.copy(active = false) else it }) }
        changesFlow.tryEmit(projectId)
        Unit
    }

    suspend fun candidates(projectId: String): List<EditCandidate> = locked(projectId) { _, git ->
        val untracked = git.status().call().untracked
        store.catalogFlow.first().edits.filter { it.projectId == projectId }.flatMap { edit ->
            val original = paths.resolve(projectId, edit.path)
            val parent = edit.path.substringBeforeLast('/', "")
            untracked.filter { path ->
                path.substringBeforeLast('/', "") == parent && File(path).name !in edit.existingPaths && isCopyName(original.name, File(path).name)
            }.mapNotNull { candidate ->
                try {
                    val result = EditCandidate(projectId, edit.path, candidate, paths.fingerprint(original), paths.fingerprint(paths.resolve(projectId, candidate)))
                    result.takeIf { it.signature !in edit.dismissed }
                } catch (_: AGitException) { null }
            }
        }
    }

    private fun isCopyName(original: String, candidate: String): Boolean {
        val file = File(original)
        val base = Regex.escape(file.nameWithoutExtension)
        val extension = if (file.extension.isEmpty()) "" else "\\.${Regex.escape(file.extension)}"
        return candidate.matches(Regex("$base(?: \\([0-9]+\\)|[-_](?:copy|edited|[0-9]+))$extension", RegexOption.IGNORE_CASE)) ||
            candidate.matches(Regex("${Regex.escape(original)}\\.(?:bak|new|edited|[0-9]+)", RegexOption.IGNORE_CASE))
    }

    suspend fun resolveCandidate(candidate: EditCandidate, overwrite: Boolean) = locked(candidate.projectId) { _, _ ->
        if (overwrite) {
            requireEditingFinished(candidate.projectId)
            val original = paths.resolve(candidate.projectId, candidate.original)
            val copy = paths.resolve(candidate.projectId, candidate.candidate)
            if (paths.fingerprint(original) != candidate.originalHash || paths.fingerprint(copy) != candidate.candidateHash) {
                throw AGitException(appString(R.string.copy_changed), "COPY_CHANGED")
            }
            // 同一目录内原子替换；候选文件保留，便于用户核查和恢复。
            val temporary = File.createTempFile(".agit-replace-", ".tmp", original.parentFile)
            try {
                copy.copyTo(temporary, overwrite = true)
                if (paths.fingerprint(original) != candidate.originalHash || paths.fingerprint(temporary) != candidate.candidateHash) {
                    throw AGitException(appString(R.string.file_writing), "FILE_WRITING")
                }
                Files.move(temporary.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        store.update { catalog -> catalog.copy(edits = catalog.edits.map { edit ->
            if (edit.projectId == candidate.projectId && edit.path == candidate.original) edit.copy(dismissed = edit.dismissed + candidate.signature) else edit
        }) }
        changesFlow.tryEmit(candidate.projectId)
        Unit
    }

    suspend fun fingerprints(projectId: String, selected: Set<String>): Map<String, String> = locked(projectId) { _, _ ->
        selected.associateWith {
            try { paths.fingerprint(paths.resolve(projectId, it)) } catch (_: AGitException) { "unsupported" }
        }
    }

    suspend fun commit(projectId: String, selected: Set<String>, expected: Map<String, String>, message: String, reporter: OperationReporter) = locked(projectId) { _, git ->
        reporter.start(0, "git status --short")
        requireEditingFinished(projectId)
        val available = snapshotFor(projectId, git).changes.flatMap { it.paths }.toSet()
        if (!available.containsAll(selected)) throw AGitException(appString(R.string.selection_changed), "SELECTION_CHANGED")
        selected.forEach { path ->
            if (paths.fingerprint(paths.resolve(projectId, path)) != expected[path]) throw AGitException(appString(R.string.content_changed), "CONTENT_CHANGED")
        }
        reporter.complete(0, appString(R.string.commit_checked))
        reporter.start(1, "git commit --only -- " + selected.sorted().joinToString(" ") { shellDisplay(it) })
        val id = engine.commit(git, selected, store.catalogFlow.first().author, message)
        reporter.complete(1, appString(R.string.commit_created, id.take(8)))
        changesFlow.tryEmit(projectId)
        Unit
    }

    suspend fun update(projectId: String, reporter: OperationReporter) = locked(projectId) { project, git ->
        val advertised = fetch(project, git, reporter, 0)
        reporter.start(1, "git status --short && git rev-list --left-right --count HEAD...@{upstream}")
        val snapshot = snapshotFor(projectId, git, advertised)
        snapshot.problem?.let { throw AGitException(it) }
        if (snapshot.behind != 0) {
            requireEditingFinished(projectId)
            if (snapshot.dirty) throw AGitException(appString(R.string.dirty_update), "DIRTY_UPDATE")
            if (snapshot.ahead != 0) throw AGitException(appString(R.string.diverged), "DIVERGED")
        }
        reporter.complete(1, appString(R.string.sync_counts, snapshot.ahead!!, snapshot.behind!!))
        if (snapshot.behind == 0) reporter.skip(2, appString(R.string.no_update)) else {
            // 临近文件写入再次扫描，避免使用 fetch 之前的 dirty 状态。
            if (snapshotFor(projectId, git).dirty) throw AGitException(appString(R.string.changed_update), "CHANGED_UPDATE")
            reporter.start(2, "git merge --ff-only origin/${snapshot.target?.removePrefix("refs/heads/")}")
            engine.fastForward(git, snapshot)
            reporter.complete(2, appString(R.string.ff_done))
        }
        changesFlow.tryEmit(projectId)
    }

    suspend fun preparePush(projectId: String, reporter: OperationReporter): PushPlan = locked(projectId) { project, git ->
        val advertised = fetch(project, git, reporter, 0)
        reporter.start(1, "git rev-list --left-right --count HEAD...@{upstream}")
        val snapshot = snapshotFor(projectId, git, advertised)
        snapshot.problem?.let { throw AGitException(it) }
        val plan = PushPlan(project.id, project.name, snapshot.branch, snapshot.target!!, snapshot.head!!, snapshot.commits, snapshot.dirty, snapshot.behind != 0)
        reporter.complete(1, appString(R.string.push_counts, snapshot.ahead!!, snapshot.behind!!))
        plan
    }

    suspend fun push(plan: PushPlan, temporary: Boolean, reporter: OperationReporter, offset: Int = 0) = locked(plan.projectId) { project, git ->
        // 确认预览后重新 fetch，远端状态不能沿用预览时的结果。
        val advertised = fetch(project, git, reporter, offset)
        reporter.start(offset + 1, "git rev-list --left-right --count HEAD...@{upstream}")
        val snapshot = snapshotFor(plan.projectId, git, advertised)
        if (snapshot.head != plan.head || snapshot.branch != plan.branch) throw AGitException(appString(R.string.head_changed), "HEAD_CHANGED")
        snapshot.problem?.let { throw AGitException(it) }
        if (!temporary && snapshot.behind != 0) throw PushBlockedException(plan.copy(dirty = snapshot.dirty, commits = snapshot.commits, blocked = true), appString(R.string.remote_ahead))
        reporter.complete(offset + 1, appString(R.string.push_checked))
        var target = plan.target
        if (temporary) {
            requireEditingFinished(plan.projectId)
            val branch = engine.temporaryBranchName(git, advertised)
            reporter.start(offset + 2, "git switch -c ${shellDisplay(branch)}")
            // 先登记目标，进程中断或推送失败后可核对同名分支并继续普通推送。
            store.update { it.copy(pendingBranches = it.pendingBranches + (project.id to branch)) }
            engine.createTemporaryBranch(git, branch)
            target = "refs/heads/$branch"
            reporter.complete(offset + 2, appString(R.string.temporary_created, branch))
        }
        val pushIndex = offset + if (temporary) 3 else 2
        reporter.start(pushIndex, "git push origin HEAD:$target")
        val token = tokenForGroup(project.groupId)
        try {
            var transportFailure: org.eclipse.jgit.api.errors.TransportException? = null
            val outcome = try { engine.push(git, target, token) } catch (error: org.eclipse.jgit.api.errors.TransportException) { transportFailure = error; null }
            if (outcome?.accepted != true) {
                val failure = transportFailure?.let(::safeFailure)
                reporter.fail(pushIndex, outcome?.message ?: failure!!.message)
                val verifyIndex = pushIndex + 1
                reporter.start(verifyIndex, "git fetch --prune origin")
                val refs = try { engine.fetch(git, token, currentCoroutineContext().job) } catch (error: CancellationException) { throw error } catch (error: Exception) {
                    val reason = failure ?: safeFailure(error)
                    throw AGitException(if (reason.code in setOf("AUTHENTICATION", "PERMISSION", "NOT_FOUND")) reason.message else appString(R.string.push_uncertain), reason.code, error)
                }
                reporter.complete(verifyIndex, appString(R.string.fetch_done))
                val remote = "refs/remotes/origin/${target.removePrefix("refs/heads/")}"
                val includes = if (target in refs) git.repository.resolve(remote)?.let { tip ->
                    org.eclipse.jgit.revwalk.RevWalk(git.repository).use { walk ->
                        walk.isMergedInto(walk.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(plan.head)), walk.parseCommit(tip))
                    }
                } == true else false
                if (!includes) {
                    if (outcome?.nonFastForward == true) {
                        val fresh = snapshotFor(project.id, git, refs)
                        if (fresh.problem == null && fresh.behind != 0) throw PushBlockedException(
                            plan.copy(branch = fresh.branch, target = fresh.target!!, head = fresh.head!!, commits = fresh.commits, dirty = fresh.dirty, blocked = true), appString(R.string.remote_ahead))
                    }
                    throw AGitException(outcome?.message ?: failure?.message ?: appString(R.string.push_uncertain), failure?.code ?: "PUSH_REJECTED", transportFailure)
                }
                // 网络中断但远端已包含 HEAD 时，确认成功而不重复执行 push。
                reporter.complete(pushIndex, appString(R.string.push_confirmed))
            }
        } finally { token.fill('\u0000') }
        if (temporary || store.catalogFlow.first().pendingBranches[project.id] == snapshot.branch) {
            engine.setUpstream(git, target.removePrefix("refs/heads/"))
            store.update { it.copy(pendingBranches = it.pendingBranches - project.id) }
        }
        reporter.complete(pushIndex, appString(R.string.push_confirmed))
        changesFlow.tryEmit(project.id)
    }

    private fun shellDisplay(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private suspend fun snapshotFor(projectId: String, git: Git, advertised: Set<String>? = null): RepositorySnapshot =
        engine.snapshot(git, advertised != null, advertised, store.catalogFlow.first().pendingBranches[projectId])

    private suspend fun fetch(project: GitProject, git: Git, reporter: OperationReporter, index: Int): Set<String> {
        reporter.start(index, "git fetch --prune origin", cancellable = true)
        val token = tokenForGroup(project.groupId)
        val refs = try { engine.fetch(git, token, currentCoroutineContext().job) } finally { token.fill('\u0000') }
        currentCoroutineContext().ensureActive()
        reporter.complete(index, appString(R.string.fetch_done))
        return refs
    }

    private suspend fun requireEditingFinished(projectId: String) {
        if (store.catalogFlow.first().edits.any { it.projectId == projectId && it.active }) throw AGitException(appString(R.string.edit_not_finished), "EDIT_NOT_FINISHED")
    }

    private suspend fun tokenForGroup(groupId: String): CharArray {
        val group = store.catalogFlow.first().groups.firstOrNull { it.id == groupId } ?: throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
        return try { vault.decrypt(group.id, group.encryptedToken) } catch (_: Exception) { throw AGitException(appString(R.string.decrypt_failed), "DECRYPT_FAILED") }
    }

    private suspend fun <T> lockedProject(projectId: String, block: suspend (GitProject) -> T): T = withContext(Dispatchers.IO) {
        locks.getOrPut(projectId) { Mutex() }.withLock {
            val project = store.catalogFlow.first().projects.firstOrNull { it.id == projectId } ?: throw AGitException(appString(R.string.missing_project), "MISSING_PROJECT")
            if (!Files.isDirectory(paths.root(projectId).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw AGitException(appString(R.string.repo_missing), "REPO_MISSING")
            }
            block(project)
        }
    }

    private suspend fun <T> locked(projectId: String, block: suspend (GitProject, Git) -> T): T = lockedProject(projectId) { project ->
        if (!project.hasGit) throw AGitException(appString(R.string.no_git_read_only), "NO_GIT_READ_ONLY")
        engine.open(project) { git -> block(project, git) }
    }
}
