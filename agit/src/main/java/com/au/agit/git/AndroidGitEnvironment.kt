package com.au.agit.git

import android.content.Context
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.FS_POSIX
import org.eclipse.jgit.util.SystemReader
import java.io.File

/** 为内嵌 JGit 隔离配置和可执行文件探测，不依赖手机安装 git 或 shell。 */
class AndroidGitEnvironment(context: Context) {
    val fs: FS = object : FS_POSIX() {
        init { setUserHome(context.filesDir); setUmask(0x3f) }
        override fun newInstance(): FS = this
        override fun discoverGitExe(): File? = null
        override fun findHook(repository: Repository, hookName: String): File? = null
    }

    init {
        val configDir = File(context.noBackupFilesDir, "jgit").apply { mkdirs() }
        val system = SystemReader.getInstance()
        SystemReader.setInstance(object : SystemReader() {
            override fun getHostname(): String = "android"
            override fun getenv(variable: String): String? = system.getenv(variable)
            override fun getProperty(key: String): String? = system.getProperty(key)
            override fun getCurrentTime(): Long = system.currentTime
            override fun getTimezone(timestamp: Long): Int = system.getTimezone(timestamp)
            override fun openSystemConfig(parent: Config?, fs: FS): FileBasedConfig = emptyConfig(parent, "system")
            override fun openUserConfig(parent: Config?, fs: FS): FileBasedConfig = emptyConfig(parent, "user")
            override fun openJGitConfig(parent: Config?, fs: FS): FileBasedConfig = emptyConfig(parent, "jgit")
            private fun emptyConfig(parent: Config?, name: String) = FileBasedConfig(parent, File(configDir, name), this@AndroidGitEnvironment.fs)
        })
        // Android 没有 JMX；在首次读取 pack 前关闭桌面 JVM 的监控注册。
        WindowCacheConfig().apply {
            setExposeStatsViaJmx(false)
            setPackedGitOpenFiles(32)
            setPackedGitLimit(16L * 1024 * 1024)
            setStreamFileThreshold(8 * 1024 * 1024)
        }.install()
    }
}
