package com.au.agit.git

import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import com.au.agit.model.CommitDiff
import com.au.agit.model.CommitDiffKind
import com.au.agit.model.FileDiscardAction
import com.au.agit.model.FileDiscardPlan
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.WorkingTreeOptions
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFileAttributes
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant

private const val DIFF_INPUT_LIMIT = 128 * 1024
private const val DIFF_OUTPUT_LIMIT = 64 * 1024

/** 对确认路径执行精确的工作区操作，diff 使用逐路径提交时的工作区内容。 */
class WorktreeChanges(private val paths: RepositoryPaths) {
    fun prepareDiscard(git: Git, projectId: String, path: String, expectedHead: String?, expectedFingerprint: String): FileDiscardPlan {
        val repo = git.repository
        val plan = discardPlan(repo, projectId, path, repo.readDirCache())
        if (plan.head != expectedHead || plan.fingerprint != expectedFingerprint) discardChanged()
        val status = git.status().addPath(path).call()
        val changes = status.added + status.changed + status.removed + status.modified + status.missing + status.untracked
        if (path !in changes || path in status.conflicting) discardChanged()
        return plan
    }

    fun discard(git: Git, projectId: String, plan: FileDiscardPlan) {
        val repo = git.repository
        val index = repo.lockDirCache()
        try {
            if (discardPlan(repo, projectId, plan.path, index) != plan) discardChanged()
            val file = paths.resolve(projectId, plan.path)
            when (plan.action) {
                FileDiscardAction.DeleteUntracked -> {
                    if (plan.path !in git.status().addPath(plan.path).call().untracked) discardChanged()
                    checkWorktree(projectId, plan)
                    Files.delete(file.toPath())
                }
                FileDiscardAction.RemoveStagedAddition -> {
                    val editor = index.editor()
                    editor.add(DirCacheEditor.DeletePath(plan.path))
                    editor.finish()
                    applyIndexedDiscard(repo, index, projectId, plan, file.toPath())
                }
                FileDiscardAction.RestoreFromHead -> restoreHead(repo, index, projectId, plan, file)
            }
        } finally {
            index.unlock()
        }
    }

    private fun discardPlan(repo: Repository, projectId: String, path: String, index: DirCache): FileDiscardPlan {
        if (repo.repositoryState != RepositoryState.SAFE) throw AGitException(appString(R.string.repo_operation_pending), "REPO_OPERATION_PENDING")
        val file = paths.resolve(projectId, path)
        val head = repo.resolve(Constants.HEAD)
        val atHead = head?.let { id ->
            RevWalk(repo).use { walk ->
                TreeWalk.forPath(repo, walk.objectReader, path, walk.parseCommit(id).tree)?.use { tree ->
                    requireFileMode(tree.getFileMode(0))
                    true
                }
            }
        } == true
        val indexEntry = index.getEntry(path)
        if (indexEntry != null) {
            if (indexEntry.stage != DirCacheEntry.STAGE_0) discardChanged()
            requireFileMode(indexEntry.fileMode)
        }
        val action = when {
            atHead -> FileDiscardAction.RestoreFromHead
            indexEntry != null -> FileDiscardAction.RemoveStagedAddition
            else -> FileDiscardAction.DeleteUntracked
        }
        val fingerprint = paths.fingerprint(file)
        val permissions = if (fingerprint == "missing") null else Files.getPosixFilePermissions(file.toPath(), NOFOLLOW_LINKS)
        return FileDiscardPlan(path, action, head?.name, indexEntry?.let { "${it.objectId.name}:${it.rawMode}" }, fingerprint, permissions)
    }

    private fun restoreHead(repo: Repository, index: DirCache, projectId: String, plan: FileDiscardPlan, file: File) {
        RevWalk(repo).use { walk ->
            val tree = walk.parseCommit(repo.resolve(Constants.HEAD) ?: discardChanged()).tree
            TreeWalk.forPath(repo, walk.objectReader, plan.path, tree)?.use { head ->
                val blob = head.getObjectId(0)
                val mode = head.getFileMode(0)
                requireFileMode(mode)
                val editor = index.editor()
                editor.add(object : DirCacheEditor.PathEdit(plan.path) {
                    override fun apply(entry: DirCacheEntry) {
                        entry.setObjectId(blob)
                        entry.fileMode = mode
                        entry.setLength(0)
                        entry.setLastModified(Instant.EPOCH)
                    }
                }.setReplace(false))
                // 禁止以文件覆盖索引中的目录或父路径。
                editor.finish()
                Files.createDirectories(file.toPath().parent)
                checkWorktree(projectId, plan)
                val temporary = Files.createTempFile(repo.directory.toPath(), "agit-restore-", ".tmp")
                try {
                    head.setOperationType(TreeWalk.OperationType.CHECKOUT_OP)
                    val metadata = DirCacheCheckout.CheckoutMetadata(head.getEolStreamType(TreeWalk.OperationType.CHECKOUT_OP), null)
                    val options = repo.config.get(WorkingTreeOptions.KEY)
                    val digest = MessageDigest.getInstance("SHA-256")
                    Files.newOutputStream(temporary).use { output ->
                        DirCacheCheckout.getContent(repo, plan.path, metadata, walk.objectReader.open(blob), options, DigestOutputStream(output, digest))
                    }
                    // 替换已有文件时保留原权限；新恢复的文件按 HEAD 设置执行位。
                    plan.permissions?.let { Files.setPosixFilePermissions(temporary, it) }
                    if ((options.isFileMode || plan.permissions == null) && repo.fs.supportsExecute() &&
                        !repo.fs.setExecute(temporary.toFile(), mode == FileMode.EXECUTABLE_FILE)
                    ) {
                        throw IOException("Cannot restore executable mode")
                    }
                    applyIndexedDiscard(repo, index, projectId, plan, file.toPath(), temporary, digest.digest().toHexString())
                } finally {
                    temporary.toFile().delete()
                }
            } ?: discardChanged()
        }
    }

    private fun applyIndexedDiscard(
        repo: Repository,
        index: DirCache,
        projectId: String,
        plan: FileDiscardPlan,
        file: Path,
        replacement: Path? = null,
        replacementFingerprint: String? = null,
    ) {
        index.write()
        val backupDirectory = if (plan.fingerprint == "missing") null else Files.createTempDirectory(repo.directory.toPath(), "agit-discard-")
        val sourcePath = backupDirectory?.resolve("path")
        val backup = backupDirectory?.resolve("original")
        var keepBackup = false
        try {
            if (sourcePath != null) Files.write(sourcePath, plan.path.toByteArray())
            if (backup != null && replacement != null) Files.copy(file, backup, COPY_ATTRIBUTES, NOFOLLOW_LINKS)
            if (repo.resolve(Constants.HEAD)?.name != plan.head) discardChanged()
            checkWorktree(projectId, plan)
            // 工作区操作成功后才提交索引，避免工作区操作失败时丢失原暂存内容。
            val replacementAttributes = when {
                replacement != null && backup != null -> {
                    val attributes = Files.readAttributes(replacement, PosixFileAttributes::class.java, NOFOLLOW_LINKS)
                    Files.move(replacement, file, ATOMIC_MOVE, REPLACE_EXISTING)
                    attributes
                }
                replacement != null -> {
                    // 独占创建缺失路径，避免覆盖校验后新建的文件。
                    Files.copy(replacement, file, COPY_ATTRIBUTES, NOFOLLOW_LINKS)
                    Files.readAttributes(file, PosixFileAttributes::class.java, NOFOLLOW_LINKS)
                }
                else -> {
                    if (backup != null) Files.move(file, backup, ATOMIC_MOVE)
                    null
                }
            }
            // 提交抛异常时结果可能已生效，保留备份供核对。
            keepBackup = backup != null
            if (index.commit()) {
                keepBackup = false
                return
            }
            val error = IOException("Cannot commit index update")
            try {
                if (backup != null && (paths.fingerprint(backup.toFile()) != plan.fingerprint ||
                    Files.getPosixFilePermissions(backup, NOFOLLOW_LINKS) != plan.permissions)
                ) discardChanged()
                val current = paths.resolve(projectId, plan.path)
                if (replacementAttributes != null) {
                    if (paths.fingerprint(current) != replacementFingerprint) discardChanged()
                    val attributes = Files.readAttributes(current.toPath(), PosixFileAttributes::class.java, NOFOLLOW_LINKS)
                    if (attributes.fileKey() != replacementAttributes.fileKey() ||
                        attributes.lastModifiedTime() != replacementAttributes.lastModifiedTime() ||
                        attributes.permissions() != replacementAttributes.permissions()
                    ) discardChanged()
                }
                when {
                    replacement != null && backup != null -> Files.move(backup, current.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                    replacement != null -> Files.delete(current.toPath())
                    backup != null -> Files.copy(backup, current.toPath(), COPY_ATTRIBUTES, NOFOLLOW_LINKS)
                }
                keepBackup = false
            } catch (rollbackError: Exception) {
                // 回滚失败时原文件留在 Git 元数据目录内，避免覆盖同路径的新修改。
                error.addSuppressed(rollbackError)
            }
            throw error
        } finally {
            if (!keepBackup) {
                backup?.toFile()?.delete()
                sourcePath?.toFile()?.delete()
                backupDirectory?.toFile()?.delete()
            }
        }
    }

    private fun checkWorktree(projectId: String, plan: FileDiscardPlan) {
        val file = paths.resolve(projectId, plan.path)
        if (paths.fingerprint(file) != plan.fingerprint ||
            (plan.permissions != null && Files.getPosixFilePermissions(file.toPath(), NOFOLLOW_LINKS) != plan.permissions)
        ) discardChanged()
    }

    private fun discardChanged(): Nothing = throw AGitException(appString(R.string.discard_changed), "DISCARD_CHANGED")

    fun commitDiff(git: Git, projectId: String, path: String, oldPath: String?): CommitDiff {
        val repo = git.repository
        val index = repo.readDirCache()
        if (listOfNotNull(path, oldPath).any { index.getEntry(it)?.stage?.let { stage -> stage != DirCacheEntry.STAGE_0 } == true }) {
            throw AGitException(appString(R.string.conflicts_unsupported), "CONFLICTS_UNSUPPORTED")
        }
        RevWalk(repo).use { walk ->
            val head = repo.resolve(Constants.HEAD)?.let { walk.parseCommit(it).tree }
            val current = diffSides(repo, index, head, projectId, path)
            val old = if (oldPath == null) current.first else {
                val previous = diffSides(repo, index, head, projectId, oldPath)
                if (previous.first.mode == FileMode.MISSING || previous.second.mode != FileMode.MISSING ||
                    current.first.mode != FileMode.MISSING || current.second.mode == FileMode.MISSING
                ) throw AGitException(appString(R.string.selection_changed), "SELECTION_CHANGED")
                previous.first
            }
            return formatDiff(old, current.second, oldPath != null)
        }
    }

    private fun diffSides(repo: Repository, index: DirCache, head: RevTree?, projectId: String, path: String): Pair<DiffSide, DiffSide> {
        val file = paths.resolve(projectId, path).toPath()
        if (Files.exists(file, NOFOLLOW_LINKS) && !Files.isRegularFile(file, NOFOLLOW_LINKS)) unsupportedFile()
        TreeWalk(repo).use { tree ->
            tree.setOperationType(TreeWalk.OperationType.CHECKIN_OP)
            if (head == null) tree.addTree(EmptyTreeIterator()) else tree.addTree(head)
            val indexTree = tree.addTree(DirCacheIterator(index))
            val workingTree = FileTreeIterator(repo).apply { setDirCacheIterator(tree, indexTree) }
            val work = tree.addTree(workingTree)
            val filter = PathFilter.create(path)
            tree.filter = filter
            while (tree.next()) {
                if (!filter.isDone(tree)) {
                    if (tree.isSubtree) tree.enterSubtree()
                    continue
                }
                val oldMode = tree.getFileMode(0)
                val working = tree.getTree(work, FileTreeIterator::class.java)
                val staged = tree.getTree(indexTree, DirCacheIterator::class.java)
                val newMode = working?.getIndexFileMode(staged) ?: FileMode.MISSING
                requireFileMode(oldMode)
                requireFileMode(newMode)
                if (working != null && oldMode == FileMode.MISSING && staged == null && working.isEntryIgnored) {
                    throw AGitException(appString(R.string.selection_changed), "SELECTION_CHANGED")
                }
                val oldBytes = if (oldMode == FileMode.MISSING) ByteArray(0) else {
                    val loader = tree.objectReader.open(tree.getObjectId(0), Constants.OBJ_BLOB)
                    if (loader.size > DIFF_INPUT_LIMIT) null else loader.getBytes(DIFF_INPUT_LIMIT)
                }
                val newBytes = when {
                    working == null -> ByteArray(0)
                    working.entryLength > DIFF_INPUT_LIMIT -> null
                    else -> working.openEntryStream().use { stream ->
                        val buffer = ByteArray(DIFF_INPUT_LIMIT + 1)
                        var offset = 0
                        while (offset < buffer.size) {
                            val count = stream.read(buffer, offset, buffer.size - offset)
                            if (count < 0) break
                            offset += count
                        }
                        if (offset > DIFF_INPUT_LIMIT) null else buffer.copyOf(offset)
                    }
                }
                return DiffSide(oldMode, oldBytes) to DiffSide(newMode, newBytes)
            }
        }
        val missing = DiffSide(FileMode.MISSING, ByteArray(0))
        return missing to missing
    }

    private fun formatDiff(old: DiffSide, new: DiffSide, renamed: Boolean): CommitDiff {
        val mode = if (old.mode != new.mode) old.mode.toString() to new.mode.toString() else null
        val oldBytes = old.bytes ?: return CommitDiff(CommitDiffKind.TooLarge, modeChange = mode)
        val newBytes = new.bytes ?: return CommitDiff(CommitDiffKind.TooLarge, modeChange = mode)
        if (oldBytes.contentEquals(newBytes)) {
            val kind = if (renamed || mode != null) CommitDiffKind.MetadataOnly else CommitDiffKind.Unchanged
            return CommitDiff(kind, modeChange = mode)
        }
        if (isBinary(oldBytes) || isBinary(newBytes)) return CommitDiff(CommitDiffKind.Binary, modeChange = mode)
        val before = RawText(oldBytes)
        val after = RawText(newBytes)
        val algorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
        val edits = algorithm.diff(RawTextComparator.DEFAULT, before, after)
        val output = DiffOutput()
        var truncated = false
        try {
            DiffFormatter(output).use { it.format(edits, before, after) }
        } catch (_: DiffLimitReached) {
            truncated = true
        }
        val patch = output.patch()
        return CommitDiff(CommitDiffKind.Text, patch, truncated, mode)
    }

    private fun isBinary(bytes: ByteArray): Boolean = RawText.isBinary(bytes) || try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
        false
    } catch (_: CharacterCodingException) {
        true
    }

    private fun requireFileMode(mode: FileMode) {
        if (mode != FileMode.MISSING && mode != FileMode.REGULAR_FILE && mode != FileMode.EXECUTABLE_FILE) unsupportedFile()
    }

    private fun unsupportedFile(): Nothing = throw AGitException(appString(R.string.regular_files_only), "REGULAR_FILES_ONLY")

    private data class DiffSide(val mode: FileMode, val bytes: ByteArray?)

    private class DiffLimitReached : IOException()

    private class DiffOutput : OutputStream() {
        private val bytes = ByteArrayOutputStream()

        override fun write(value: Int) {
            if (bytes.size() == DIFF_OUTPUT_LIMIT) throw DiffLimitReached()
            bytes.write(value)
        }

        override fun write(buffer: ByteArray, start: Int, length: Int) {
            val remaining = DIFF_OUTPUT_LIMIT - bytes.size()
            if (length > remaining) {
                bytes.write(buffer, start, remaining)
                throw DiffLimitReached()
            }
            bytes.write(buffer, start, length)
        }

        fun patch(): String = bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
