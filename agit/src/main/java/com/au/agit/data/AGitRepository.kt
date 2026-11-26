package com.au.agit.data

import com.au.agit.R
import com.au.agit.appString
import android.content.Context
import android.content.Intent
import com.au.agit.git.JGitEngine
import com.au.agit.git.RepositoryPaths
import com.au.agit.git.commitsForTemporaryRepublish
import com.au.agit.git.hasPendingBranchSwitch
import com.au.agit.git.initialBranchChoices
import com.au.agit.git.publishedInitialBranchChoices
import com.au.agit.git.isTemporaryBranch
import com.au.agit.git.recoverBranchSwitch
import com.au.agit.git.recordTemporaryPublication
import com.au.agit.git.rememberTemporaryTrackingTip
import com.au.agit.git.requireCleanBranchSwitch
import com.au.agit.git.returnBranchChoices
import com.au.agit.git.switchToRemoteChoice
import com.au.agit.git.WorktreeChanges
import com.au.agit.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import com.au.module_android.log.loge
import com.au.module_android.utilsmedia.getUriFromFile
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
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
    private val worktreeChanges = WorktreeChanges(paths)
    val changesFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val credentialLock = Mutex()
    private val cloneTempDirectory = File(context.filesDir, "temp/clones")
    private val cloneProjectIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

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

    suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        val projectIds = credentialLock.withLock {
            val catalog = store.catalogFlow.first()
            if (catalog.groups.none { it.id == groupId }) throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
            val ids = catalog.projects.filter { it.groupId == groupId }.map { it.id }.toSet()
            confirmDeletion(ids, groupId)
            ids
        }
        cleanupConfirmedDeletions(projectIds)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val projectIds = setOf(projectId)
        credentialLock.withLock {
            if (store.catalogFlow.first().projects.none { it.id == projectId }) {
                throw AGitException(appString(R.string.missing_project), "MISSING_PROJECT")
            }
            confirmDeletion(projectIds, groupId = null)
        }
        cleanupConfirmedDeletions(projectIds)
    }

    private suspend fun confirmDeletion(projectIds: Set<String>, groupId: String?) {
        val acquiredLocks = mutableListOf<Mutex>()
        try {
            projectIds.sorted().forEach { id ->
                paths.root(id)
                val lock = locks.getOrPut(id) { Mutex() }
                lock.lock()
                acquiredLocks += lock
            }
            // 先保存已确认的删除和索引变更，进程中断后可继续清理磁盘。
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                store.update { current ->
                    if (current.edits.any { it.active && it.projectId in projectIds }) {
                        throw AGitException(appString(R.string.edit_not_finished), "EDIT_NOT_FINISHED")
                    }
                    val remainingGroups = current.groups.filterNot { it.id == groupId }
                    val remainingProjects = current.projects.filterNot { it.id in projectIds }
                    val selectedGroup = remainingGroups.firstOrNull { it.id == current.currentGroup?.id }
                        ?: remainingGroups.firstOrNull()
                    val selectedProject = remainingProjects.firstOrNull { it.id == current.currentProject?.id && it.groupId == selectedGroup?.id }
                        ?: remainingProjects.firstOrNull { it.groupId == selectedGroup?.id }
                    current.copy(
                        groups = remainingGroups,
                        projects = remainingProjects,
                        selectedGroupId = selectedGroup?.id,
                        selectedProjectId = selectedProject?.id,
                        edits = current.edits.filterNot { it.projectId in projectIds },
                        pendingBranches = current.pendingBranches - projectIds,
                        pendingDeletionProjectIds = current.pendingDeletionProjectIds + projectIds,
                    )
                }
            }
        } finally {
            acquiredLocks.asReversed().forEach { it.unlock() }
        }
    }

    suspend fun retryPendingDeletions() = withContext(Dispatchers.IO) {
        cleanupConfirmedDeletions(store.catalogFlow.first().pendingDeletionProjectIds)
    }

    private suspend fun cleanupConfirmedDeletions(projectIds: Set<String>) {
        var failure: Exception? = null
        projectIds.forEach { id ->
            locks.getOrPut(id) { Mutex() }.withLock {
                try {
                    val catalog = store.catalogFlow.first()
                    if (id in catalog.pendingDeletionProjectIds) {
                        if (catalog.projects.any { it.id == id }) {
                            throw AGitException(appString(R.string.project_exists), "PROJECT_EXISTS")
                        }
                        deleteDirectory(paths.root(id).toPath())
                        store.update { it.copy(pendingDeletionProjectIds = it.pendingDeletionProjectIds - id) }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    loge { "project deletion cleanup failed project=$id type=${error.javaClass.simpleName}" }
                    if (failure == null) failure = error
                }
            }
        }
        failure?.let { throw AGitException(appString(R.string.delete_cleanup_pending), "DELETE_CLEANUP_PENDING", it) }
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

    suspend fun clone(groupId: String, projectId: String, input: String, reporter: OperationReporter): GitProject = withContext(Dispatchers.IO) {
        if (!projectId.matches(cloneProjectIdPattern)) throw AGitException(appString(R.string.invalid_project_id), "INVALID_PROJECT_ID")
        val url = engine.normalizeUrl(input)
        val project = GitProject(projectId, groupId, url.substringAfterLast('/').removeSuffix(".git"), url)
        locks.getOrPut(project.id) { Mutex() }.withLock {
            val directory = paths.root(project.id).toPath()
            val temporaryDirectory = File(cloneTempDirectory, project.id).toPath()
            val catalog = store.catalogFlow.first()
            if (project.id in catalog.pendingDeletionProjectIds) {
                throw AGitException(appString(R.string.project_exists), "PROJECT_EXISTS")
            }
            catalog.projects.firstOrNull { it.id == project.id }?.let { registered ->
                if (registered != project) throw AGitException(appString(R.string.project_exists), "PROJECT_EXISTS")
                reporter.complete(0, appString(R.string.clone_complete))
                return@withLock registered
            }
            if (!Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw AGitException(appString(R.string.project_exists), "PROJECT_EXISTS")
            }
            // 同一请求的未完成下载可重试，正式目录只在本次移动成功后清理。
            deleteDirectory(temporaryDirectory)
            val token = tokenForGroup(groupId)
            val job = currentCoroutineContext().job
            var moved = false
            try {
                reporter.start(0, "git clone $url ${project.id}", cancellable = true)
                val receivedBytesFlow = MutableStateFlow(0L)
                coroutineScope {
                    val progressJob = launch {
                        receivedBytesFlow.collect { bytes ->
                            if (bytes > 0) reporter.progress(0, bytes)
                            delay(250.milliseconds)
                        }
                    }
                    try {
                        engine.clone(project, token, job, temporaryDirectory.toFile()) { receivedBytesFlow.value = it }
                    } finally {
                        progressJob.cancelAndJoin()
                    }
                }
                if (receivedBytesFlow.value > 0) reporter.progress(0, receivedBytesFlow.value)
                currentCoroutineContext().ensureActive()
                reporter.start(0, "git clone $url ${project.id}", cancellable = false)
                withContext(NonCancellable) {
                    credentialLock.withLock {
                        if (store.catalogFlow.first().groups.none { it.id == groupId }) {
                            throw AGitException(appString(R.string.missing_group), "MISSING_GROUP")
                        }
                        // JGit 已完成下载、检出并关闭仓库，之后才原子改名并登记到正式目录。
                        Files.createDirectories(directory.parent)
                        Files.move(temporaryDirectory, directory, StandardCopyOption.ATOMIC_MOVE)
                        moved = true
                        store.update { catalog ->
                            catalog.copy(projects = catalog.projects + project, selectedGroupId = groupId, selectedProjectId = project.id)
                        }
                    }
                }
                reporter.complete(0, appString(R.string.clone_complete))
                project
            } catch (error: Exception) {
                // 保持项目锁，等待下载和回写退出后清理，取消期间也必须完成删除。
                withContext(NonCancellable) {
                    deleteDirectory(temporaryDirectory)
                    if (moved && store.catalogFlow.first().projects.none { it.id == project.id }) {
                        deleteDirectory(directory)
                    }
                }
                throw error
            } finally { token.fill('\u0000') }
        }
    }

    suspend fun cleanupIncompleteClones() = withContext(Dispatchers.IO) {
        val projectIds = listOf(cloneTempDirectory, File(context.filesDir, "shared"))
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .map { it.name }.filter { it.matches(cloneProjectIdPattern) }.distinct()
        projectIds.forEach { id ->
            currentCoroutineContext().ensureActive()
            // 与 Clone 复用项目锁；本次进程里仍在写入的目录必须等待其完成，不能提前删除。
            locks.getOrPut(id) { Mutex() }.withLock {
                val catalog = store.catalogFlow.first()
                deleteDirectory(File(cloneTempDirectory, id).toPath())
                if (catalog.projects.none { it.id == id } && id !in catalog.pendingDeletionProjectIds) {
                    deleteDirectory(paths.root(id).toPath())
                }
            }
        }
    }

    private fun deleteDirectory(directory: Path) {
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    suspend fun snapshot(projectId: String): RepositorySnapshot = locked(projectId) { _, git -> snapshotFor(projectId, git) }

    suspend fun files(projectId: String, directory: String): List<RepositoryFile> = locked(projectId) { _, _ ->
        val root = paths.resolve(projectId, directory, allowRoot = true)
        if (!root.exists()) return@locked emptyList()
        if (!root.isDirectory) throw AGitException(appString(R.string.missing_directory), "MISSING_DIRECTORY")
        root.listFiles()?.filter { it.name != ".git" }?.map { file ->
            val path = if (directory.isEmpty()) file.name else "$directory/${file.name}"
            RepositoryFile(path, file.name, !Files.isSymbolicLink(file.toPath()) && file.isDirectory, file.length())
        }?.sortedWith(compareByDescending<RepositoryFile> { it.directory }.thenBy { it.name.lowercase() })
            ?: throw AGitException(appString(R.string.unreadable_directory), "UNREADABLE_DIRECTORY")
    }

    suspend fun file(projectId: String, path: String): File = locked(projectId) { _, _ ->
        paths.resolve(projectId, path).also { if (!it.isFile) throw AGitException(appString(R.string.missing_file), "MISSING_FILE") }
    }

    suspend fun prepareDiscard(projectId: String, path: String, head: String?, fingerprint: String): FileDiscardPlan = locked(projectId) { _, git ->
        requireEditingFinished(projectId)
        worktreeChanges.prepareDiscard(git, projectId, path, head, fingerprint)
    }

    suspend fun discardFile(projectId: String, plan: FileDiscardPlan) = locked(projectId) { _, git ->
        try {
            requireEditingFinished(projectId)
            worktreeChanges.discard(git, projectId, plan)
        } finally {
            changesFlow.tryEmit(projectId)
        }
        Unit
    }

    suspend fun commitDiff(projectId: String, path: String, oldPath: String?): CommitDiff = locked(projectId) { _, git ->
        worktreeChanges.commitDiff(git, projectId, path, oldPath)
    }

    suspend fun createFile(projectId: String, directory: String, name: String): CreatedFile = locked(projectId) { _, git ->
        if (name.isBlank() || name == "." || name == ".." || name.equals(".git", ignoreCase = true) ||
            name.any { it == '/' || it == '\\' || it.isISOControl() }
        ) {
            throw AGitException(appString(R.string.invalid_file_name), "INVALID_FILE_NAME")
        }
        requireEditingFinished(projectId)
        if (!paths.resolve(projectId, directory, allowRoot = true).isDirectory) {
            throw AGitException(appString(R.string.missing_directory), "MISSING_DIRECTORY")
        }
        val path = if (directory.isEmpty()) name else "$directory/$name"
        val file = paths.resolve(projectId, path)
        try {
            Files.createFile(file.toPath())
        } catch (_: FileAlreadyExistsException) {
            throw AGitException(appString(R.string.file_already_exists), "FILE_ALREADY_EXISTS")
        }
        changesFlow.tryEmit(projectId)
        val ignoredByGit = try {
            git.status().addPath(path).call().ignoredNotInIndex.any { it == path || path.startsWith("$it/") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loge { "git ignore check failed project=$projectId type=${error.javaClass.simpleName}" }
            false
        }
        CreatedFile(path, ignoredByGit)
    }

    suspend fun beginEdit(
        projectId: String,
        path: String,
        prepareIntent: (File) -> Intent,
        launch: (Intent) -> Unit,
    ) = locked(projectId) { _, _ ->
        val file = paths.resolve(projectId, path)
        if (!file.isFile) throw AGitException(appString(R.string.missing_file), "MISSING_FILE")
        requireEditingFinished(projectId)
        val intent = prepareIntent(file)
        val siblings = file.parentFile?.listFiles()?.map { it.name }?.toSet().orEmpty()
        val session = EditSession(projectId, path, siblings)
        val previous = store.catalogFlow.first().edits.firstOrNull { it.projectId == projectId && it.path == path }
        var launched = false
        try {
            store.update { catalog ->
                val edits = if (previous == null) catalog.edits + session else catalog.edits.map { if (it == previous) session else it }
                catalog.copy(edits = edits)
            }
            withContext(Dispatchers.Main.immediate) {
                launch(intent)
                launched = true
            }
        } finally {
            // 启动返回后立刻标记成功；页面随后离开时仍需保留编辑会话。
            if (!launched) withContext(NonCancellable) {
                store.update { catalog -> catalog.copy(edits = catalog.edits.mapNotNull { if (it == session) previous else it }) }
            }
        }
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
            val parent = edit.path.substringBeforeLast('/', "")
            val originalName = File(edit.path).name
            val possibleCopies = untracked.filter { path ->
                val name = File(path).name
                path.substringBeforeLast('/', "") == parent && name !in edit.existingPaths && isCopyName(originalName, name)
            }
            if (possibleCopies.isEmpty()) return@flatMap emptyList()
            val originalHash = editFileHash(projectId, edit.path) ?: return@flatMap emptyList()
            possibleCopies.mapNotNull { candidate ->
                val candidateHash = editFileHash(projectId, candidate) ?: return@mapNotNull null
                val result = EditCandidate(projectId, edit.path, candidate, originalHash, candidateHash)
                result.takeIf { it.signature !in edit.dismissed }
            }
        }
    }

    private fun editFileHash(projectId: String, path: String): String? = try {
        paths.fingerprint(paths.resolve(projectId, path)).takeIf { it != "missing" }
    } catch (_: AGitException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun isCopyName(original: String, candidate: String): Boolean {
        val file = File(original)
        val base = Regex.escape(file.nameWithoutExtension)
        val extension = if (file.extension.isEmpty()) "" else "\\.${Regex.escape(file.extension)}"
        return candidate.matches(Regex("$base(?: \\([0-9]+\\)|[-_](?:copy|edited|[0-9]+))$extension", RegexOption.IGNORE_CASE)) ||
            candidate.matches(Regex("${Regex.escape(original)}\\.(?:bak|new|edited|[0-9]+)", RegexOption.IGNORE_CASE))
    }

    suspend fun resolveCandidate(candidate: EditCandidate, overwrite: Boolean) = locked(candidate.projectId) { _, git ->
        try {
            requireEditingFinished(candidate.projectId)
            val edit = store.catalogFlow.first().edits.firstOrNull { it.projectId == candidate.projectId && it.path == candidate.original }
            if (edit == null || candidate.signature in edit.dismissed || candidate.candidate !in git.status().addPath(candidate.candidate).call().untracked ||
                editFileHash(candidate.projectId, candidate.original) != candidate.originalHash ||
                editFileHash(candidate.projectId, candidate.candidate) != candidate.candidateHash
            ) throw AGitException(appString(R.string.copy_changed), "COPY_CHANGED")
            if (overwrite) {
                val original = paths.resolve(candidate.projectId, candidate.original)
                val copy = paths.resolve(candidate.projectId, candidate.candidate)
                // 同一目录内原子替换并保留候选文件。
                val temporary = File.createTempFile(".agit-replace-", ".tmp", original.parentFile)
                try {
                    Files.newInputStream(copy.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                        temporary.outputStream().use { input.copyTo(it) }
                    }
                    if (editFileHash(candidate.projectId, candidate.original) != candidate.originalHash || paths.fingerprint(temporary) != candidate.candidateHash) {
                        throw AGitException(appString(R.string.copy_changed), "COPY_CHANGED")
                    }
                    Files.move(temporary.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally { temporary.delete() }
            }
            store.update { catalog -> catalog.copy(edits = catalog.edits.map { edit ->
                if (edit.projectId == candidate.projectId && edit.path == candidate.original) edit.copy(dismissed = edit.dismissed + candidate.signature) else edit
            }) }
        } finally {
            changesFlow.tryEmit(candidate.projectId)
        }
        Unit
    }

    suspend fun fingerprints(projectId: String, selected: Set<String>): Map<String, String> = locked(projectId) { _, _ ->
        selected.associateWith {
            try { paths.fingerprint(paths.resolve(projectId, it)) } catch (_: AGitException) { "unsupported" } catch (_: IOException) { "unsupported" }
        }
    }

    suspend fun commit(projectId: String, selected: Set<String>, expected: Map<String, String>, message: String, reporter: OperationReporter) = locked(projectId) { _, git ->
        reporter.start(0, "git status --short")
        requireEditingFinished(projectId)
        git.recoverBranchSwitch()
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

    suspend fun update(projectId: String, reporter: OperationReporter): RepositoryPlan? = locked(projectId) { project, git ->
        val advertised = fetch(project, git, reporter, 0)
        reporter.start(1, appString(R.string.check_state))
        val snapshot = snapshotFor(projectId, git, advertised)
        if (snapshot.head == null) {
            val choices = git.repository.initialBranchChoices(advertised)
            if (choices.isNotEmpty()) {
                requireEditingFinished(projectId)
                git.requireCleanBranchSwitch()
                reporter.complete(1, appString(R.string.remote_branch_available))
                return@locked BranchChoicePlan(projectId, snapshot.branch, null, choices)
            }
        }
        if (snapshot.temporaryBranch && snapshot.head != null && Constants.R_HEADS + snapshot.branch !in advertised) {
            return@locked returnBranchPlan(project, git, snapshot, advertised, reporter, 1)
        }
        snapshot.problem?.let { throw AGitException(it) }
        if (snapshot.initialPushConflict) {
            reporter.complete(1, appString(R.string.push_checked))
            return@locked publishedInitialBranchPlan(projectId, git, snapshot, advertised) ?: pushPlan(project, snapshot, advertised)
        }
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
        null
    }

    suspend fun prepareReturn(projectId: String, reporter: OperationReporter): BranchChoicePlan = locked(projectId) { project, git ->
        val advertised = fetch(project, git, reporter, 0)
        reporter.start(1, appString(R.string.check_state))
        returnBranchPlan(project, git, snapshotFor(projectId, git, advertised), advertised, reporter, 1)
    }

    private suspend fun returnBranchPlan(
        project: GitProject,
        git: Git,
        snapshot: RepositorySnapshot,
        advertised: Set<String>,
        reporter: OperationReporter,
        index: Int,
    ): BranchChoicePlan {
        val head = snapshot.head?.takeIf { snapshot.temporaryBranch }
            ?: throw AGitException(appString(R.string.return_source_missing), "RETURN_SOURCE_MISSING")
        requireEditingFinished(project.id)
        git.requireCleanBranchSwitch()
        val choices = git.repository.returnBranchChoices(snapshot.branch, ObjectId.fromString(head), advertised)
        if (choices.isEmpty()) throw AGitException(appString(R.string.return_source_missing), "RETURN_SOURCE_MISSING")
        reporter.complete(index, appString(R.string.return_branch_ready))
        return BranchChoicePlan(project.id, snapshot.branch, head, choices)
    }

    suspend fun confirmBranchChoice(plan: BranchChoicePlan, choice: BranchChoice, reporter: OperationReporter, offset: Int) =
        locked(plan.projectId) { project, git ->
            val advertised = fetch(project, git, reporter, offset)
            reporter.start(offset + 1, appString(R.string.check_state))
            val repo = git.repository
            val head = repo.resolve(Constants.HEAD)
            if (repo.fullBranch != Constants.R_HEADS + plan.currentBranch || head?.name != plan.currentHead) {
                throw AGitException(appString(R.string.head_changed), "HEAD_CHANGED")
            }
            requireEditingFinished(project.id)
            val choices = when {
                head == null -> repo.initialBranchChoices(advertised)
                plan.choosingInitialUpstream -> repo.publishedInitialBranchChoices(plan.currentBranch, head, advertised)
                else -> {
                    if (!repo.isTemporaryBranch(plan.currentBranch) && store.catalogFlow.first().pendingBranches[project.id] != plan.currentBranch) {
                        throw AGitException(appString(R.string.head_changed), "HEAD_CHANGED")
                    }
                    repo.returnBranchChoices(plan.currentBranch, head, advertised)
                }
            }
            if (choice !in choices || choice !in plan.choices) {
                throw AGitException(appString(R.string.branch_choice_changed), "BRANCH_CHOICE_CHANGED")
            }
            reporter.complete(offset + 1, appString(R.string.branch_switch_checked))
            reporter.start(offset + 2, "git switch ${shellDisplay(choice.branch)}")
            git.switchToRemoteChoice(choice)
            reporter.complete(offset + 2, appString(R.string.branch_checked_out, choice.branch))
            changesFlow.tryEmit(project.id)
        }

    suspend fun preparePush(projectId: String, reporter: OperationReporter): RepositoryPlan = locked(projectId) { project, git ->
        val advertised = fetch(project, git, reporter, 0)
        reporter.start(1, appString(R.string.check_state))
        val snapshot = pushSnapshot(projectId, git, advertised)
        snapshot.problem?.let { throw AGitException(it) }
        val plan = pushPlan(project, snapshot, advertised)
        val result = if (plan.initialPushConflict) appString(R.string.push_checked)
        else appString(R.string.push_counts, snapshot.ahead!!, snapshot.behind!!)
        reporter.complete(1, result)
        publishedInitialBranchPlan(projectId, git, snapshot, advertised) ?: plan
    }

    private fun publishedInitialBranchPlan(projectId: String, git: Git, snapshot: RepositorySnapshot, advertised: Set<String>): BranchChoicePlan? {
        if (!snapshot.initialPushConflict || snapshot.commits.isNotEmpty()) return null
        val head = checkNotNull(snapshot.head)
        val choices = git.repository.publishedInitialBranchChoices(snapshot.branch, ObjectId.fromString(head), advertised)
        return if (choices.isEmpty()) null else BranchChoicePlan(projectId, snapshot.branch, head, choices, choosingInitialUpstream = true)
    }

    private fun pushPlan(project: GitProject, snapshot: RepositorySnapshot, advertised: Set<String>) = PushPlan(
        project.id, project.name, snapshot.branch, snapshot.target, checkNotNull(snapshot.head), snapshot.commits,
        snapshot.dirty, blocked = snapshot.initialPushConflict || snapshot.behind != 0,
        recreateTemporary = snapshot.temporaryBranch && snapshot.target !in advertised,
        allowTemporary = !snapshot.initialPush, initialPushConflict = snapshot.initialPushConflict,
    )

    suspend fun push(plan: PushPlan, temporary: Boolean, reporter: OperationReporter, offset: Int = 0): BranchChoicePlan? = locked(plan.projectId) { project, git ->
        // 确认预览后重新 fetch，远端状态不能沿用预览时的结果。
        val advertised = fetch(project, git, reporter, offset)
        reporter.start(offset + 1, appString(R.string.check_state))
        val snapshot = pushSnapshot(plan.projectId, git, advertised)
        if (snapshot.head != plan.head || snapshot.branch != plan.branch) throw AGitException(appString(R.string.head_changed), "HEAD_CHANGED")
        snapshot.problem?.let { throw AGitException(it) }
        val publishedPlan = publishedInitialBranchPlan(project.id, git, snapshot, advertised)
        if (publishedPlan != null) {
            reporter.complete(offset + 1, appString(R.string.push_checked))
            return@locked publishedPlan
        }
        val checkedPlan = pushPlan(project, snapshot, advertised).copy(protectionRejected = plan.protectionRejected)
        if (checkedPlan.initialPushConflict && !plan.initialPushConflict) {
            reporter.complete(offset + 1, appString(R.string.push_checked))
            throw PushBlockedException(checkedPlan, checkedPlan.blockedMessage())
        }
        if (checkedPlan.target != plan.target || checkedPlan.recreateTemporary != plan.recreateTemporary) {
            throw AGitException(appString(R.string.head_changed), "HEAD_CHANGED")
        }
        if (checkedPlan.blocked && (!temporary || !checkedPlan.canCreateTemporaryBranch)) {
            throw PushBlockedException(checkedPlan, checkedPlan.blockedMessage())
        }
        if (temporary && (!plan.canCreateTemporaryBranch || !checkedPlan.canCreateTemporaryBranch)) {
            throw AGitException(appString(R.string.head_changed), "HEAD_CHANGED")
        }
        if (checkedPlan.commits.isEmpty()) throw AGitException(appString(R.string.no_outgoing), "NO_OUTGOING")
        reporter.complete(offset + 1, appString(R.string.push_checked))
        val target = if (temporary) {
            requireEditingFinished(plan.projectId)
            val branch = engine.temporaryBranchName(git, advertised)
            reporter.start(offset + 2, "git switch -c ${shellDisplay(branch)}")
            // 先登记目标，进程中断或推送失败后可核对同名分支并继续普通推送。
            store.update { it.copy(pendingBranches = it.pendingBranches + (project.id to branch)) }
            engine.createTemporaryBranch(git, branch, plan.branch, plan.target)
            reporter.complete(offset + 2, appString(R.string.temporary_created, branch))
            Constants.R_HEADS + branch
        } else checkNotNull(plan.target)
        val pushIndex = offset + if (temporary) 3 else 2
        reporter.start(pushIndex, "git push origin HEAD:$target")
        val pendingTemporary = temporary || store.catalogFlow.first().pendingBranches[project.id] == snapshot.branch
        val createOnly = snapshot.initialPush || checkedPlan.recreateTemporary || (pendingTemporary && target !in advertised)
        if (snapshot.initialPush) engine.recordInitialPushAttempt(git, snapshot.branch, plan.head)
        val token = tokenForGroup(project.groupId)
        try {
            var transportFailure: org.eclipse.jgit.api.errors.TransportException? = null
            val outcome = try {
                engine.push(git, target, token, createOnly = createOnly)
            } catch (error: org.eclipse.jgit.api.errors.TransportException) {
                transportFailure = error
                null
            }
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
                if (!temporary && !snapshot.initialPush && outcome?.nonFastForward == true) {
                    val fresh = snapshotFor(project.id, git, refs)
                    if (fresh.problem == null && fresh.behind != 0) {
                        val blockedPlan = plan.copy(commits = fresh.commits, dirty = fresh.dirty, blocked = true)
                        throw PushBlockedException(blockedPlan, blockedPlan.blockedMessage())
                    }
                }
                val includes = target in refs && engine.remoteIncludes(git, target, ObjectId.fromString(plan.head))
                if (!includes) {
                    if (snapshot.initialPush) {
                        val fresh = snapshotFor(project.id, git, refs)
                        if (fresh.problem == null && fresh.initialPushConflict) {
                            val choicePlan = publishedInitialBranchPlan(project.id, git, fresh, refs)
                            if (choicePlan != null) return@locked choicePlan
                            val blockedPlan = pushPlan(project, fresh, refs)
                            throw PushBlockedException(blockedPlan, blockedPlan.blockedMessage())
                        }
                    }
                    if (!temporary && !createOnly && outcome?.protectedBranch == true && target in refs) {
                        val fresh = snapshotFor(project.id, git, refs)
                        if (fresh.problem == null && fresh.commits.isNotEmpty()) {
                            val protectedPlan = checkedPlan.copy(commits = fresh.commits, dirty = fresh.dirty,
                                blocked = fresh.behind != 0, protectionRejected = true)
                            throw PushBlockedException(protectedPlan, protectedPlan.blockedMessage())
                        }
                    }
                    if (outcome?.protectedBranch == true) {
                        throw AGitException(outcome.message, "BRANCH_PROTECTED")
                    }
                    throw AGitException(outcome?.message ?: failure?.message ?: appString(R.string.push_uncertain), failure?.code ?: "PUSH_REJECTED", transportFailure)
                }
                // 网络中断但远端已包含 HEAD 时，确认成功而不重复执行 push。
                reporter.complete(pushIndex, appString(R.string.push_confirmed))
            }
        } finally { token.fill('\u0000') }
        if (snapshot.initialPush || pendingTemporary) {
            engine.setUpstream(git, target.removePrefix("refs/heads/"))
        }
        git.repository.recordTemporaryPublication(target.removePrefix("refs/heads/"), ObjectId.fromString(plan.head))
        if (pendingTemporary) {
            store.update { it.copy(pendingBranches = it.pendingBranches - project.id) }
        }
        reporter.complete(pushIndex, appString(R.string.push_confirmed))
        changesFlow.tryEmit(project.id)
        null
    }

    private fun shellDisplay(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private suspend fun snapshotFor(projectId: String, git: Git, advertised: Set<String>? = null): RepositorySnapshot =
        engine.snapshot(git, advertised != null, advertised, store.catalogFlow.first().pendingBranches[projectId])

    private suspend fun pushSnapshot(projectId: String, git: Git, advertised: Set<String>): RepositorySnapshot {
        val snapshot = snapshotFor(projectId, git, advertised)
        val head = snapshot.head
        val target = Constants.R_HEADS + snapshot.branch
        if (!snapshot.temporaryBranch || head == null || snapshot.target != target || target in advertised ||
            git.repository.repositoryState != org.eclipse.jgit.lib.RepositoryState.SAFE
        ) return snapshot
        val commits = git.repository.commitsForTemporaryRepublish(snapshot.branch, ObjectId.fromString(head), advertised)
        return snapshot.copy(commits = commits, ahead = commits.size, behind = 0, problem = null)
    }

    private suspend fun fetch(project: GitProject, git: Git, reporter: OperationReporter, index: Int): Set<String> {
        reporter.start(index, "git fetch --prune origin", cancellable = true)
        if (git.repository.hasPendingBranchSwitch()) {
            requireEditingFinished(project.id)
            git.recoverBranchSwitch()
        }
        git.repository.rememberTemporaryTrackingTip()
        val token = tokenForGroup(project.groupId)
        val refs = try { engine.fetch(git, token, currentCoroutineContext().job) } finally { token.fill('\u0000') }
        currentCoroutineContext().ensureActive()
        engine.recoverInitialPush(git, refs)
        val pendingBranch = store.catalogFlow.first().pendingBranches[project.id]
        if (pendingBranch != null && engine.recoverTemporaryPush(git, pendingBranch, refs)) {
            store.update { it.copy(pendingBranches = it.pendingBranches - project.id) }
        }
        git.repository.rememberTemporaryTrackingTip()
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

    private suspend fun <T> locked(projectId: String, block: suspend (GitProject, Git) -> T): T = withContext(Dispatchers.IO) {
        locks.getOrPut(projectId) { Mutex() }.withLock {
            val project = store.catalogFlow.first().projects.firstOrNull { it.id == projectId } ?: throw AGitException(appString(R.string.missing_project), "MISSING_PROJECT")
            engine.open(project) { git -> block(project, git) }
        }
    }
}
