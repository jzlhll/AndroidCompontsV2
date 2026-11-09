package com.au.agit.git

import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** 只实现 origin 的普通 Git 操作；所有调用由 AGitRepository 在 IO 线程串行执行。 */
class JGitEngine(private val environment: AndroidGitEnvironment, private val paths: RepositoryPaths) {
    fun normalizeUrl(input: String): String {
        val uri = try { URI(input.trim()) } catch (_: Exception) { throw AGitException(appString(R.string.invalid_url), "INVALID_URL") }
        if (uri.scheme != "https" || uri.host != "github.com" || uri.rawUserInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null) {
            throw AGitException(appString(R.string.https_only), "HTTPS_ONLY")
        }
        val parts = uri.path.trim('/').split('/')
        if (parts.size != 2 || parts.any { !it.matches(Regex("[A-Za-z0-9_.-]+")) || it in setOf(".", "..") }) {
            throw AGitException(appString(R.string.invalid_repo_path), "INVALID_REPO_PATH")
        }
        val name = parts[1].removeSuffix(".git")
        if (name.isBlank()) throw AGitException(appString(R.string.invalid_repo_name), "INVALID_REPO_NAME")
        return "https://github.com/${parts[0]}/$name.git"
    }

    fun clone(project: GitProject, token: CharArray, job: Job, directory: File, onReceivedBytes: (Long) -> Unit) {
        if (directory.exists()) throw AGitException(appString(R.string.project_exists), "PROJECT_EXISTS")
        directory.parentFile?.mkdirs()
        val receivedBytes = AtomicLong()
        val connectionFactory = GitDownloadConnectionFactory { count ->
            job.ensureActive()
            onReceivedBytes(receivedBytes.addAndGet(count))
        }
        credentials(token) { provider ->
            val command = Git.cloneRepository().setURI(project.url).setDirectory(directory).setFs(environment.fs)
                .setCloneSubmodules(false).setCredentialsProvider(provider).setTimeout(180)
                .setTransportConfigCallback { transport ->
                    if (transport is TransportHttp) transport.httpConnectionFactory = connectionFactory
                }
                .setProgressMonitor(monitor(job))
            if (project.cloneMode == CloneMode.Depth) command.setDepth(project.depth!!)
            command.call().use { git -> configure(git.repository) }
        }
    }

    suspend fun <T> open(project: GitProject, block: suspend (Git) -> T): T {
        val root = paths.root(project.id)
        if (!root.resolve(".git").isDirectory) throw AGitException(appString(R.string.repo_missing), "REPO_MISSING")
        val repository = FileRepositoryBuilder().setFS(environment.fs).setGitDir(root.resolve(".git"))
            .setWorkTree(root).setMustExist(true).build()
        return repository.use { Git(it).use { git ->
            val urls = repository.config.getStringList("remote", "origin", "url")
            val pushUrls = repository.config.getStringList("remote", "origin", "pushurl")
            if (urls.size != 1 || normalizeUrl(urls.single()) != project.url || pushUrls.isNotEmpty()) {
                throw AGitException(appString(R.string.origin_changed), "ORIGIN_CHANGED")
            }
            if (repository.config.getSubsections("filter").isNotEmpty()) throw AGitException(appString(R.string.filters_unsupported), "FILTERS_UNSUPPORTED")
            block(git)
        } }
    }

    private fun configure(repository: Repository) {
        repository.config.apply {
            setBoolean("core", null, "filemode", false)
            setBoolean("core", null, "logallrefupdates", true)
            setString("http", null, "followRedirects", "false")
            save()
        }
    }

    fun snapshot(git: Git, fetched: Boolean = false, advertised: Set<String>? = null, pendingBranch: String? = null): RepositorySnapshot {
        val repo = git.repository
        val changes = changes(git)
        val head = repo.resolve(Constants.HEAD)
        val fullBranch = repo.fullBranch.orEmpty()
        val branch = Repository.shortenRefName(fullBranch)
        val config = BranchConfig(repo.config, branch)
        val unpublished = pendingBranch == branch
        val target = if (unpublished) "refs/heads/$branch" else config.merge?.takeIf { config.remote == "origin" && it.startsWith(Constants.R_HEADS) }
        val tracking = if (unpublished) "refs/remotes/origin/$branch" else config.remoteTrackingBranch
        var problem = when {
            repo.repositoryState != RepositoryState.SAFE -> appString(R.string.repo_unsafe)
            !fullBranch.startsWith(Constants.R_HEADS) -> appString(R.string.detached_head)
            head == null -> appString(R.string.unborn_head)
            target == null || tracking == null -> appString(R.string.missing_upstream)
            !unpublished && advertised != null && target !in advertised -> appString(R.string.remote_deleted)
            else -> null
        }
        val remote = tracking?.let { repo.exactRef(it)?.objectId }
        if (problem == null && remote == null && !unpublished) problem = appString(R.string.missing_tracking)
        var ahead: Int? = null
        var behind: Int? = null
        var commits = emptyList<CommitSummary>()
        if (problem == null && unpublished && remote == null && head != null) {
            commits = git.log().call().map { CommitSummary(it.name, it.shortMessage) }
            ahead = commits.size
            behind = 0
        }
        if (problem == null && head != null && remote != null) {
            RevWalk(repo).use { walk ->
                walk.revFilter = RevFilter.MERGE_BASE
                walk.markStart(walk.parseCommit(head))
                walk.markStart(walk.parseCommit(remote))
                if (walk.next() == null) problem = appString(
                    if (repo.objectDatabase.shallowCommits.isNotEmpty()) R.string.shallow_history_incomplete else R.string.unrelated_history
                )
            }
            if (problem == null) {
                commits = git.log().addRange(remote, head).call().map { CommitSummary(it.name, it.shortMessage) }
                ahead = commits.size
                behind = git.log().addRange(head, remote).call().count()
            }
        }
        return RepositorySnapshot(branch, target, head?.name, changes, ahead, behind, commits, problem, fetched)
    }

    private fun changes(git: Git): List<FileChange> {
        val status = git.status().call()
        val staged = status.added + status.changed + status.removed
        val all = (staged + status.modified + status.missing + status.untracked + status.conflicting).toSortedSet()
        val result = all.associateWith { path ->
            FileChange(path, when (path) {
                in status.conflicting -> appString(R.string.change_conflict)
                in status.added, in status.untracked -> appString(R.string.change_added)
                in status.removed, in status.missing -> appString(R.string.change_deleted)
                else -> appString(R.string.change_modified)
            }, staged = path in staged, type = when (path) {
                in status.conflicting -> ChangeType.Conflicted
                in status.added, in status.untracked -> ChangeType.Added
                in status.removed, in status.missing -> ChangeType.Deleted
                else -> ChangeType.Modified
            })
        }.toMutableMap()
        val head = git.repository.resolve(Constants.HEAD)
        if (head != null) {
            git.repository.newObjectReader().use { reader ->
                val tree = RevWalk(git.repository).use { it.parseCommit(head).tree.id }
                val parser = CanonicalTreeParser().apply { reset(reader, tree) }
                DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
                    formatter.setRepository(git.repository)
                    formatter.isDetectRenames = true
                    formatter.scan(parser, FileTreeIterator(git.repository)).filter { it.changeType == DiffEntry.ChangeType.RENAME }.forEach { entry ->
                        if (entry.oldPath in all && entry.newPath in all) {
                            result.remove(entry.oldPath)
                            result[entry.newPath] = FileChange(entry.newPath, appString(R.string.change_renamed, entry.oldPath),
                                setOf(entry.oldPath, entry.newPath), entry.oldPath in staged || entry.newPath in staged, ChangeType.Renamed)
                        }
                    }
                }
            }
        }
        return result.values.toList()
    }

    fun fetch(git: Git, token: CharArray, job: Job): Set<String> = credentials(token) { provider ->
        git.fetch().setRemote("origin").setRefSpecs(RefSpec("+refs/heads/*:refs/remotes/origin/*"))
            .setRemoveDeletedRefs(true).setCredentialsProvider(provider).setTimeout(45)
            .setProgressMonitor(monitor(job)).call().advertisedRefs.map { it.name }.toSet()
    }

    fun commit(git: Git, selected: Set<String>, author: Author, message: String): String {
        if (git.repository.repositoryState != RepositoryState.SAFE) throw AGitException(appString(R.string.repo_operation_pending), "REPO_OPERATION_PENDING")
        if (selected.isEmpty() || message.isBlank()) throw AGitException(appString(R.string.empty_commit), "EMPTY_COMMIT")
        if (!author.isConfigured) throw AGitException(appString(R.string.configure_author), "CONFIGURE_AUTHOR")
        val status = git.status().call()
        if (status.conflicting.isNotEmpty()) throw AGitException(appString(R.string.conflicts_unsupported), "CONFLICTS_UNSUPPORTED")
        // setOnly 隔离未选中的暂存项；新文件需先进入索引才能参与普通文件提交。
        selected.filter { it in status.untracked }.forEach { git.add().addFilepattern(it).call() }
        val ident = PersonIdent(author.commitName, author.email.trim())
        val command = git.commit().setMessage(message.trim()).setAuthor(ident).setCommitter(ident).setAllowEmpty(false)
        selected.forEach { command.setOnly(it) }
        return command.call().name
    }

    fun fastForward(git: Git, snapshot: RepositorySnapshot) {
        val target = BranchConfig(git.repository.config, snapshot.branch).remoteTrackingBranch
            ?: throw AGitException(appString(R.string.no_upstream), "NO_UPSTREAM")
        val result = git.merge().include(git.repository.exactRef(target)).setFastForward(MergeCommand.FastForwardMode.FF_ONLY).call()
        if (!result.mergeStatus.isSuccessful) throw AGitException(appString(R.string.ff_failed), "FF_FAILED")
    }

    fun temporaryBranchName(git: Git, advertised: Set<String>): String {
        val base = git.repository.branch.replace(Regex("[^A-Za-z0-9-]"), "-").take(40)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val name = "agit-tmp/$base-$timestamp-${UUID.randomUUID().toString().take(8)}"
        if (!Repository.isValidRefName("refs/heads/$name") || git.repository.exactRef("refs/heads/$name") != null || "refs/heads/$name" in advertised) {
            throw AGitException(appString(R.string.temporary_collision), "TEMPORARY_COLLISION")
        }
        return name
    }

    fun createTemporaryBranch(git: Git, name: String) {
        // 新分支指向同一 HEAD，不检出文件，保留未选中的工作区与暂存区。
        git.branchCreate().setName(name).setStartPoint(Constants.HEAD).setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.NOTRACK).call()
        val update = git.repository.updateRef(Constants.HEAD).link("refs/heads/$name")
        if (update !in setOf(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE)) throw AGitException(appString(R.string.branch_switch_failed), "BRANCH_SWITCH_FAILED")
    }

    fun push(git: Git, target: String, token: CharArray): PushOutcome = credentials(token) { provider ->
        val updates = git.push().setRemote("origin").setRefSpecs(RefSpec("HEAD:$target"))
            .setForce(false).setCredentialsProvider(provider).setTimeout(45).call().flatMap { it.remoteUpdates }
        if (updates.size != 1) throw AGitException(appString(R.string.push_unknown), "PUSH_UNKNOWN")
        val update = updates.single()
        when (update.status) {
            RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE -> PushOutcome(true, false, appString(R.string.push_complete))
            RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD, RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> PushOutcome(false, true, appString(R.string.remote_ahead))
            else -> PushOutcome(false, false, appString(R.string.push_rejected))
        }
    }

    fun setUpstream(git: Git, branch: String) {
        git.repository.config.apply {
            setString("branch", branch, "remote", "origin")
            setString("branch", branch, "merge", "refs/heads/$branch")
            save()
        }
    }

    private fun monitor(job: Job) = object : ProgressMonitor {
        override fun start(totalTasks: Int) = Unit
        override fun beginTask(title: String?, totalWork: Int) = Unit
        override fun update(completed: Int) = Unit
        override fun endTask() = Unit
        override fun showDuration(enabled: Boolean) = Unit
        override fun isCancelled(): Boolean = !job.isActive
    }

    private fun <T> credentials(token: CharArray, block: (CredentialsProvider) -> T): T {
        val supplied = mutableListOf<CredentialItem.Password>()
        val provider = object : CredentialsProvider() {
            override fun isInteractive(): Boolean = false
            override fun supports(vararg items: CredentialItem): Boolean = items.all { it is CredentialItem.Username || it is CredentialItem.Password }
            override fun get(uri: URIish, vararg items: CredentialItem): Boolean {
                if (uri.scheme != "https" || uri.host != "github.com" || !supports(*items)) return false
                items.forEach { when (it) {
                    is CredentialItem.Username -> it.value = "x-access-token"
                    is CredentialItem.Password -> { it.value = token; supplied.add(it) }
                } }
                return true
            }
        }
        return try { block(provider) } finally { supplied.forEach { it.clear() } }
    }
}
