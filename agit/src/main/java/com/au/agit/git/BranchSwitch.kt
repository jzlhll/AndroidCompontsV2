package com.au.agit.git

import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import com.au.agit.model.BranchChoice
import com.au.agit.model.CommitSummary
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.NameConflictTreeWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes

private const val TEMPORARY_PREFIX = "agit-tmp/"
private const val SOURCE_BRANCH = "agitSourceBranch"
private const val SOURCE_TARGET = "agitSourceTarget"
private const val PUBLISHED_HEAD = "agitPublishedHead"
private const val SWITCH_SECTION = "agit"
private const val SWITCH_SUBSECTION = "branchSwitch"
private val legacyTemporaryName = Regex("agit-tmp/[A-Za-z0-9-]{1,40}-[0-9]{8}-[0-9]{6}-[a-f0-9]{8}")

private data class PendingBranchSwitch(
    val source: String,
    val sourceHead: ObjectId?,
    val destination: String,
    val destinationHead: ObjectId?,
    val target: String,
    val remoteHead: ObjectId,
    val configureUpstream: Boolean,
)

fun Repository.isTemporaryBranch(branch: String): Boolean {
    if (!branch.startsWith(TEMPORARY_PREFIX)) return false
    val upstream = BranchConfig(config, branch)
    return config.getString("branch", branch, SOURCE_BRANCH) != null ||
        (legacyTemporaryName.matches(branch) && upstream.remote == "origin" && upstream.merge == Constants.R_HEADS + branch)
}

fun Repository.recordTemporarySource(branch: String, source: String, target: String?) {
    val originalSource = config.getString("branch", source, SOURCE_BRANCH)
    val originalTarget = if (originalSource == null) target else config.getString("branch", source, SOURCE_TARGET)
    config.setString("branch", branch, SOURCE_BRANCH, originalSource ?: source)
    if (originalTarget == null) config.unset("branch", branch, SOURCE_TARGET)
    else config.setString("branch", branch, SOURCE_TARGET, originalTarget)
    config.save()
}

fun Repository.recordTemporaryPublication(branch: String, head: ObjectId) {
    if (!isTemporaryBranch(branch) || config.getString("branch", branch, PUBLISHED_HEAD) == head.name) return
    config.setString("branch", branch, PUBLISHED_HEAD, head.name)
    config.save()
}

fun Repository.rememberTemporaryTrackingTip() {
    val branch = fullBranch?.takeIf { it.startsWith(Constants.R_HEADS) }?.removePrefix(Constants.R_HEADS) ?: return
    if (!isTemporaryBranch(branch)) return
    val head = resolve(Constants.HEAD) ?: return
    val tracking = exactRef(originTrackingRef(Constants.R_HEADS + branch))?.objectId ?: return
    // 剪枝前记住已从 origin 看见的本地提交，兼容升级前创建的分支。
    if (containsCommit(tracking, head)) recordTemporaryPublication(branch, head)
}

fun originTrackingRef(target: String): String = Constants.R_REMOTES + "origin/" + target.removePrefix(Constants.R_HEADS)

fun Repository.initialBranchChoices(advertised: Set<String>): List<BranchChoice> {
    if (repositoryState != RepositoryState.SAFE || resolve(Constants.HEAD) != null ||
        fullBranch?.startsWith(Constants.R_HEADS) != true || refDatabase.getRefsByPrefix(Constants.R_HEADS).isNotEmpty()
    ) return emptyList()
    return advertised.filter { it.startsWith(Constants.R_HEADS) }.sorted().mapNotNull { target ->
        val tip = exactRef(originTrackingRef(target))?.objectId ?: return@mapNotNull null
        BranchChoice(target.removePrefix(Constants.R_HEADS), target, tip.name)
    }
}

fun Repository.publishedInitialBranchChoices(branch: String, head: ObjectId, advertised: Set<String>): List<BranchChoice> {
    if (!hasPendingInitialPush(branch)) return emptyList()
    return advertised.filter { it.startsWith(Constants.R_HEADS) }.sorted().mapNotNull { target ->
        val tip = exactRef(originTrackingRef(target))?.objectId ?: return@mapNotNull null
        // 直接设置上游只允许前进到已包含全部本地提交的分支。
        if (!containsCommit(tip, head)) return@mapNotNull null
        BranchChoice(branch, target, tip.name, head.name, merged = true, configureUpstream = true)
    }
}

fun Repository.returnBranchChoices(branch: String, head: ObjectId, advertised: Set<String>): List<BranchChoice> {
    val source = config.getString("branch", branch, SOURCE_BRANCH)
    val sourceTarget = config.getString("branch", branch, SOURCE_TARGET)
    val publishedHead = config.getString("branch", branch, PUBLISHED_HEAD)?.let(ObjectId::fromString)
    val knownDelivered = publishedHead != null && containsCommit(publishedHead, head)
    val legacyDeliveryUnknown = source == null && publishedHead == null &&
        Constants.R_HEADS + branch !in advertised
    val candidates = if (source != null) {
        listOfNotNull(exactRef(Constants.R_HEADS + source))
    } else {
        refDatabase.getRefsByPrefix(Constants.R_HEADS).filter {
            val name = it.name.removePrefix(Constants.R_HEADS)
            name != branch && !isTemporaryBranch(name)
        }
    }
    var unpublished = false
    val choices = candidates.flatMap { ref ->
        val name = ref.name.removePrefix(Constants.R_HEADS)
        if (ref.isSymbolic || name == branch) return@flatMap emptyList()
        val local = ref.objectId ?: return@flatMap emptyList()
        val configureUpstream = source == name && sourceTarget == null && hasPendingInitialPush(name)
        val targets = if (configureUpstream) {
            // 首发被抢先时，返回来源分支才明确选择要跟踪的 origin 分支。
            advertised.filter { it.startsWith(Constants.R_HEADS) && it != Constants.R_HEADS + branch }.sorted()
        } else {
            val tracking = BranchConfig(config, name)
            listOfNotNull(tracking.merge?.takeIf {
                it.startsWith(Constants.R_HEADS) && it in advertised && tracking.remote == "origin" &&
                    (sourceTarget == null || sourceTarget == it)
            })
        }
        val localPreserved = targets.isNotEmpty() && containsCommit(head, local)
        targets.mapNotNull { target ->
            val remote = exactRef(originTrackingRef(target))?.objectId ?: return@mapNotNull null
            // 移动来源分支前，确保远端新 tip 或保留的本地临时分支仍可访问原有提交。
            if (!localPreserved && !containsCommit(remote, local)) return@mapNotNull null
            val merged = containsCommit(remote, head)
            if (!merged && !knownDelivered && !legacyDeliveryUnknown) {
                unpublished = true
                return@mapNotNull null
            }
            BranchChoice(name, target, remote.name, local.name, merged, legacyDeliveryUnknown, configureUpstream)
        }
    }.sortedBy { it.branch }
    if (choices.isEmpty() && unpublished) {
        throw AGitException(appString(R.string.return_unpublished), "RETURN_UNPUBLISHED")
    }
    return choices
}

fun Repository.commitsForTemporaryRepublish(branch: String, head: ObjectId, advertised: Set<String>): List<CommitSummary> {
    val sourceTarget = config.getString("branch", branch, SOURCE_TARGET)
    val baselines = if (sourceTarget != null && sourceTarget in advertised) listOf(sourceTarget)
    else advertised.filter { it.startsWith(Constants.R_HEADS) }
    val commits = commitsOutsideRemoteBranches(head, baselines)
    // 同名远端已删除时，允许将已存在于其他分支的临时 tip 重新发布。
    return commits.ifEmpty { listOf(RevWalk(this).use { walk -> walk.parseCommit(head).let { CommitSummary(it.name, it.shortMessage) } }) }
}

fun Repository.commitsOutsideRemoteBranches(head: ObjectId, targets: List<String>): List<CommitSummary> {
    return RevWalk(this).use { walk ->
        walk.markStart(walk.parseCommit(head))
        targets.forEach { target ->
            exactRef(originTrackingRef(target))?.objectId?.let { walk.markUninteresting(walk.parseCommit(it)) }
        }
        walk.map { CommitSummary(it.name, it.shortMessage) }
    }
}

fun Git.requireCleanBranchSwitch(): Status {
    if (repository.repositoryState != RepositoryState.SAFE) {
        throw AGitException(appString(R.string.repo_unsafe), "REPO_UNSAFE")
    }
    val status = status().call()
    if (!status.isClean) throw AGitException(appString(R.string.dirty_branch_switch), "DIRTY_BRANCH_SWITCH")
    return status
}

fun Git.switchToRemoteChoice(choice: BranchChoice) {
    val repo = repository
    val destination = Constants.R_HEADS + choice.branch
    val remoteHead = ObjectId.fromString(choice.remoteHead)
    val previous = choice.localHead?.let(ObjectId::fromString)
    val index = repo.lockDirCache()
    try {
        val oldHead = repo.resolve(Constants.HEAD)
        if (repo.exactRef(destination)?.objectId != previous || repo.refDatabase.isNameConflicting(destination)) {
            throw AGitException(appString(R.string.branch_choice_changed), "BRANCH_CHOICE_CHANGED")
        }
        val pending = PendingBranchSwitch(repo.fullBranch, oldHead, destination, previous, choice.target, remoteHead,
            oldHead == null || choice.configureUpstream)
        val oldTree = oldHead?.let { commit -> RevWalk(repo).use { it.parseCommit(commit).tree.id } }
        val newTree = RevWalk(repo).use { it.parseCommit(remoteHead).tree.id }
        val ignored = requireCleanBranchSwitch().ignoredNotInIndex
        if (ignored.isNotEmpty()) {
            TreeWalk(repo).use { walk ->
                walk.addTree(newTree)
                walk.isRecursive = true
                while (walk.next()) {
                    val path = walk.pathString
                    if (index.getEntry(path) == null && ignored.any { path == it || path.startsWith("$it/") || it.startsWith("$path/") }) {
                        throw AGitException(appString(R.string.branch_ignored_collision), "BRANCH_IGNORED_COLLISION")
                    }
                }
            }
        }
        val checkout = DirCacheCheckout(repo, oldTree, index, newTree)
        checkout.setFailOnConflict(true)
        // 先写工作区，确认检出成功后再移动分支引用。同名未出生分支也按此顺序处理。
        repo.config.apply {
            setString(SWITCH_SECTION, SWITCH_SUBSECTION, "source", pending.source)
            setString(SWITCH_SECTION, SWITCH_SUBSECTION, "sourceHead", oldHead?.name.orEmpty())
            setString(SWITCH_SECTION, SWITCH_SUBSECTION, "destination", destination)
            setString(SWITCH_SECTION, SWITCH_SUBSECTION, "destinationHead", previous?.name.orEmpty())
            setString(SWITCH_SECTION, SWITCH_SUBSECTION, "target", choice.target)
            setString(SWITCH_SECTION, SWITCH_SUBSECTION, "remoteHead", remoteHead.name)
            setBoolean(SWITCH_SECTION, SWITCH_SUBSECTION, "configureUpstream", pending.configureUpstream)
            setBoolean(SWITCH_SECTION, SWITCH_SUBSECTION, "checkoutComplete", false)
            save()
        }
        try {
            if (!checkout.checkout()) throw branchSwitchRemovalPending(checkout.toBeDeleted.first())
        } catch (error: org.eclipse.jgit.errors.CheckoutConflictException) {
            repo.clearPendingBranchSwitch()
            throw AGitException(appString(R.string.branch_switch_failed), "BRANCH_SWITCH_FAILED", error)
        }
        // 记录检出已完成，避免恢复时将用户后来新建的同名文件当作残留文件。
        repo.config.setBoolean(SWITCH_SECTION, SWITCH_SUBSECTION, "checkoutComplete", true)
        repo.config.save()
        repo.finishBranchSwitch(pending)
    } finally {
        index.unlock()
    }
}

fun Repository.hasPendingBranchSwitch(): Boolean = config.getString(SWITCH_SECTION, SWITCH_SUBSECTION, "destination") != null

fun Git.recoverBranchSwitch() {
    val repo = repository
    if (!repo.hasPendingBranchSwitch()) return
    val pending = repo.config.let { config ->
        fun value(key: String) = config.getString(SWITCH_SECTION, SWITCH_SUBSECTION, key)
        val sourceHead = value("sourceHead")?.takeIf(String::isNotEmpty)?.let(ObjectId::fromString)
        PendingBranchSwitch(
            checkNotNull(value("source")),
            sourceHead,
            checkNotNull(value("destination")),
            value("destinationHead")?.takeIf(String::isNotEmpty)?.let(ObjectId::fromString),
            checkNotNull(value("target")),
            ObjectId.fromString(checkNotNull(value("remoteHead"))),
            config.getBoolean(SWITCH_SECTION, SWITCH_SUBSECTION, "configureUpstream", sourceHead == null),
        )
    }
    val head = repo.resolve(Constants.HEAD)
    val atSource = repo.fullBranch == pending.source && head == pending.sourceHead
    val atDestination = repo.fullBranch == pending.destination && head == pending.remoteHead
    val index = repo.readDirCache()
    val indexTree = if (index.hasUnmergedPaths()) null else repo.newObjectInserter().use { inserter ->
        index.writeTree(inserter)
    }
    val targetTree = RevWalk(repo).use { it.parseCommit(pending.remoteHead).tree.id }
    val sourceTree = pending.sourceHead?.let { commit -> RevWalk(repo).use { it.parseCommit(commit).tree.id } }
    val sourceIndexUnchanged = if (sourceTree == null) index.entryCount == 0 else indexTree == sourceTree
    when {
        (atSource || atDestination) && indexTree == targetTree -> {
            val checkoutComplete = repo.config.getBoolean(SWITCH_SECTION, SWITCH_SUBSECTION, "checkoutComplete", false)
            // JGit 可能在删除失败后写入目标索引，需要确认工作区中的旧路径已移除。
            if (atSource && !atDestination && !checkoutComplete && sourceTree != null) {
                repo.pendingCheckoutRemoval(sourceTree, targetTree)?.let { throw branchSwitchRemovalPending(it) }
            }
            repo.finishBranchSwitch(pending)
        }
        atSource && sourceIndexUnchanged -> repo.clearPendingBranchSwitch()
        else -> throw AGitException(appString(R.string.branch_switch_interrupted), "BRANCH_SWITCH_INTERRUPTED")
    }
}

private fun Repository.pendingCheckoutRemoval(sourceTree: ObjectId, targetTree: ObjectId): String? {
    val root = workTree.toPath()
    NameConflictTreeWalk(this).use { walk ->
        walk.addTree(sourceTree)
        walk.addTree(targetTree)
        walk.filter = TreeFilter.ANY_DIFF
        while (walk.next()) {
            val source = walk.getFileMode(0)
            val target = walk.getFileMode(1)
            if (source == FileMode.MISSING ||
                (source != FileMode.TREE && target != FileMode.MISSING && target != FileMode.TREE)
            ) continue
            val path = walk.pathString
            val attributes = try {
                Files.readAttributes(root.resolve(path), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: NoSuchFileException) {
                continue
            } catch (error: IOException) {
                throw branchSwitchRemovalPending(path, error)
            }
            if (source == FileMode.TREE) {
                if (!attributes.isDirectory) continue
                // 即使旧文件都已删除，同名目录也会阻挡目标文件。
                if (target.objectType == Constants.OBJ_BLOB) return path
                walk.enterSubtree()
            } else if (!attributes.isDirectory) {
                return path
            }
        }
    }
    return null
}

private fun branchSwitchRemovalPending(path: String, cause: IOException? = null) =
    AGitException(appString(R.string.branch_switch_removal_pending, path), "BRANCH_SWITCH_INTERRUPTED", cause)

private fun Repository.finishBranchSwitch(pending: PendingBranchSwitch) {
    val currentHead = resolve(Constants.HEAD)
    val currentRef = fullBranch
    if (!(currentRef == pending.source && currentHead == pending.sourceHead) &&
        !(currentRef == pending.destination && currentHead == pending.remoteHead)
    ) {
        throw AGitException(appString(R.string.branch_switch_interrupted), "BRANCH_SWITCH_INTERRUPTED")
    }
    val update = updateRef(pending.destination)
    val destinationRef = exactRef(pending.destination)
    if (destinationRef?.isSymbolic == true) throw AGitException(appString(R.string.branch_switch_failed), "BRANCH_SWITCH_FAILED")
    val destinationHead = destinationRef?.objectId
    if (destinationHead != pending.remoteHead) {
        update.setExpectedOldObjectId(pending.destinationHead ?: ObjectId.zeroId())
        update.setNewObjectId(pending.remoteHead)
        update.setRefLogMessage("agit: align branch with origin", false)
        val result = if (pending.destinationHead == null) update.update() else update.forceUpdate()
        if (result !in setOf(RefUpdate.Result.NEW, RefUpdate.Result.NO_CHANGE, RefUpdate.Result.FAST_FORWARD, RefUpdate.Result.FORCED)) {
            throw AGitException(appString(R.string.branch_switch_failed), "BRANCH_SWITCH_FAILED")
        }
    }
    val headUpdate = updateRef(Constants.HEAD)
    val branch = pending.destination.removePrefix(Constants.R_HEADS)
    headUpdate.setRefLogMessage("checkout: moving to " + branch, false)
    val linked = headUpdate.link(pending.destination)
    if (linked !in setOf(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.NO_CHANGE)) {
        throw AGitException(appString(R.string.branch_switch_failed), "BRANCH_SWITCH_FAILED")
    }
    if (pending.configureUpstream) setOriginUpstream(branch, pending.target)
    clearPendingBranchSwitch()
}

private fun Repository.clearPendingBranchSwitch() {
    config.unsetSection(SWITCH_SECTION, SWITCH_SUBSECTION)
    config.save()
}

private fun Repository.containsCommit(tip: ObjectId, commit: ObjectId): Boolean {
    return RevWalk(this).use { walk -> walk.isMergedInto(walk.parseCommit(commit), walk.parseCommit(tip)) }
}
