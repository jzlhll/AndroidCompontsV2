package com.allan.picnote

import android.app.Application
import android.os.Process
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.PermissionPreferences
import com.allan.picnote.data.AppearancePreferences
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.media.MediaScanner
import com.au.module_android.Globals
import com.au.module_android.log.LogTag
import com.au.module_android.log.logDebugEnabled
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID

/** 持有单模块所需的数据库与本地服务。 */
class PicNoteApplication : Application() {
    val startupId: String = UUID.randomUUID().toString()
    val processId: Int = Process.myPid()
    var foregroundOrdinal: Int = 0
        private set
    private val foregroundEvents = Channel<Int>(Channel.UNLIMITED)
    val foregroundEntries = foregroundEvents.receiveAsFlow()
    val database by lazy { Room.databaseBuilder(this, PicNoteDatabase::class.java, "picnote.db").fallbackToDestructiveMigration(dropAllTables = true).build() }
    val documents by lazy { DocumentRepository(this, database) }
    val scanner by lazy { MediaScanner(this, database) }
    val permissionPreferences by lazy { PermissionPreferences(this) }
    val appearancePreferences by lazy { AppearancePreferences(this) }

    override fun onCreate() {
        super.onCreate()
        Globals.internalApp = this
        LogTag.TAG = "PicNote"
        logDebugEnabled = true
        logd("event=app_start startupId=$startupId pid=$processId foregroundOrdinal=0")
        // 进程可见性决定检查次数，窗口焦点与 Activity 重建只用于诊断。
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foregroundOrdinal++
                    logd("event=foreground_enter startupId=$startupId pid=$processId foregroundOrdinal=$foregroundOrdinal")
                    foregroundEvents.trySend(foregroundOrdinal)
                }
                Lifecycle.Event.ON_STOP -> logd("event=background startupId=$startupId pid=$processId foregroundOrdinal=$foregroundOrdinal")
                else -> Unit
            }
        })
    }
}
