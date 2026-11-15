package com.allan.picnote

import android.os.Process
import androidx.room.Room
import com.allan.picnote.data.PicNoteDatabase
import com.allan.picnote.data.PermissionPreferences
import com.allan.picnote.data.AppearancePreferences
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.media.MediaScanner
import com.au.module_android.CommonInitApplication
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.log.logDebugEnabled
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID

/** 持有单模块所需的数据库与本地服务。 */
class PicNoteApplication : CommonInitApplication() {
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

    override fun config() = FirstInitialConfig(tag = "PicNote")

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
                    foregroundEvents.trySend(foregroundOrdinal)
                } else logd("event=background startupId=$startupId pid=$processId foregroundOrdinal=$foregroundOrdinal")
            }
        }
        GlobalBackgroundCallback.addListener(backgroundListener)
        if (GlobalBackgroundCallback.isForeground) backgroundListener.onBackground(false)
    }
}
