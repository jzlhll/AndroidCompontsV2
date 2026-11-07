package com.au.agit.git

import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/** 限制所有文件入口在当前项目内，并拒绝符号链接和 Git 元数据。 */
class RepositoryPaths(private val shared: File) {
    fun root(projectId: String): File {
        if (!projectId.matches(Regex("[a-zA-Z0-9-]+"))) throw AGitException(appString(R.string.invalid_project_id), "INVALID_PROJECT_ID")
        return File(shared, projectId)
    }

    fun resolve(projectId: String, path: String, allowRoot: Boolean = false): File {
        val root = root(projectId).canonicalFile
        if (path.isEmpty() && allowRoot) return root
        if (path.isEmpty() || File(path).isAbsolute || path.split('/').any { it == ".." || it == ".git" || it.isEmpty() }) {
            throw AGitException(appString(R.string.forbidden_path), "FORBIDDEN_PATH")
        }
        val lexical = File(root, path)
        var current = lexical
        while (current != root) {
            if (Files.isSymbolicLink(current.toPath())) throw AGitException(appString(R.string.symlink_unsupported), "SYMLINK_UNSUPPORTED")
            current = current.parentFile ?: throw AGitException(appString(R.string.path_escape), "PATH_ESCAPE")
        }
        val file = lexical.canonicalFile
        if (!file.toPath().startsWith(root.toPath()) || file == root) throw AGitException(appString(R.string.path_escape), "PATH_ESCAPE")
        return file
    }

    fun fingerprint(file: File): String {
        if (!file.exists()) return "missing"
        if (!file.isFile) throw AGitException(appString(R.string.regular_files_only), "REGULAR_FILES_ONLY")
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
