package com.allan.picnote

import android.os.Process
import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.PermissionPreferences
import com.allan.picnote.data.AppearancePreferences
import com.allan.picnote.data.NodeGuidePreferences
import com.allan.picnote.data.ImportModePreferences
import com.allan.picnote.data.CodeLanguagePreferences
import com.allan.picnote.data.CodeWrapPreferences
import com.allan.picnote.data.CleanupResult
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.media.MediaScanner
import com.allan.picnote.media.MediaDeletion
import com.allan.picnote.media.ScrollThumbnailFetcher
import com.au.module_android.CommonInitApplication
import com.au.module_android.Globals
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.log.logDebugEnabled
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File

/** 持有单模块所需的数据库与本地服务。 */
class PicNoteApplication : CommonInitApplication(), SingletonImageLoader.Factory {
    val startupId: String = UUID.randomUUID().toString()
    val processId: Int = Process.myPid()
    var foregroundOrdinal: Int = 0
        private set
    private val foregroundEvents = Channel<Int>(Channel.UNLIMITED)
    private val startupCleanup = CompletableDeferred<CleanupResult>()
    val foregroundEntries = foregroundEvents.receiveAsFlow()
    val database by lazy { Room.databaseBuilder(this, PicNoteDatabase::class.java, "picnote.db").fallbackToDestructiveMigration(dropAllTables = true).build() }
    val documents by lazy { DocumentRepository(this, database) }
    val scanner by lazy { MediaScanner(this, database) }
    val mediaDeletion by lazy { MediaDeletion(this) }
    val permissionPreferences by lazy { PermissionPreferences(this) }
    val appearancePreferences by lazy { AppearancePreferences(this) }
    val nodeGuidePreferences by lazy { NodeGuidePreferences(this) }
    val importModePreferences by lazy { ImportModePreferences(this) }
    val codeLanguagePreferences by lazy { CodeLanguagePreferences(this) }
    val codeWrapPreferences by lazy { CodeWrapPreferences(this) }

    override fun config() = FirstInitialConfig(tag = "PicNote")

    suspend fun awaitStartupCleanup(): CleanupResult = startupCleanup.await()

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(ScrollThumbnailFetcher.Factory()) }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, 1.0 / 6.0).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "coil"))
                .maxSizeBytes(400L * 1024 * 1024)
                .build()
        }
        .build()

    override fun onCreate() {
        logDebugEnabled = true
        super.onCreate()
        logd("event=app_start startupId=$startupId pid=$processId foregroundOrdinal=0")
        // 进程可见性决定检查次数，窗口焦点与 Activity 重建只用于诊断。
        val backgroundListener = object : GlobalBackgroundCallback.IBackgroundListener {
            override fun onBackground(isBackground: Boolean) {
                if (!isBackground) {
                    foregroundOrdinal++
                    logd("event=foreground_enter startupId=$startupId pid=$processId foregroundOrdinal=$foregroundOrdinal")
                    if (foregroundOrdinal == 1) {
                        Globals.backgroundScope.launch {
                            try {
                                startupCleanup.complete(documents.cleaner.clean(clearAllCache = false))
                            } catch (e: CancellationException) {
                                startupCleanup.cancel(e)
                                throw e
                            } catch (e: Exception) {
                                logEx(throwable = e, message = "Startup cleanup failed")
                                startupCleanup.complete(CleanupResult(failedTasks = setOf("startup")))
                            }
                        }
                    }
                    foregroundEvents.trySend(foregroundOrdinal)
                } else logd("event=background startupId=$startupId pid=$processId foregroundOrdinal=$foregroundOrdinal")
            }
        }
        GlobalBackgroundCallback.addListener(backgroundListener)
        if (GlobalBackgroundCallback.isForeground) backgroundListener.onBackground(false)
    }
}
