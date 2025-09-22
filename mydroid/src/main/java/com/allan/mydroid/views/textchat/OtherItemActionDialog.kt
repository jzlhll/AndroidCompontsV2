package com.allan.mydroid.views.textchat

import android.annotation.SuppressLint
import androidx.fragment.app.FragmentManager
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_android.utils.serializableCompat
import com.au.module_android.utilsmedia.UriParserUtil
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_androidui.dialogs.AbsActionDialogFragment
import kotlinx.coroutines.delay
import java.io.File
import java.lang.ref.WeakReference

class OtherItemActionDialog(private var file: File? = null) : AbsActionDialogFragment() {
    companion object {
        /////////////////////////////////////////////
        var fileExportSuccessCallback: WeakReference<((info:String, exportFileStr:String)->Unit)>? = null
        var fileExportFailCallback:WeakReference<((String)->Unit)>? = null
        /**
         * 删除后的回调
         */
        var refreshFileListCallback:WeakReference<(()->Unit)>? = null

        var importSendCallback: WeakReference<()->Unit> ?= null

        ///////////////////////////////////////////// end

        fun pop(manager: FragmentManager,
                fileExportSuccessCallback:(info:String, exportFileStr:String)->Unit = {_,_->},
                fileExportFailCallback:(String)->Unit = {},
                refreshFileListCallback:()->Unit = {},
                importSendCallback:()->Unit = {}) {
            this.fileExportSuccessCallback = WeakReference(fileExportSuccessCallback)
            this.fileExportFailCallback = WeakReference(fileExportFailCallback)
            this.refreshFileListCallback = WeakReference(refreshFileListCallback)
            this.importSendCallback = WeakReference(importSendCallback)
        }

        private const val TAG_DOWNLAOD = "download"
    }

    val mItems = listOf(
        ItemBean(TAG_DOWNLAOD, R.string.download.resStr(), R.drawable.ic_download, normalColor)
    )

    override val items: List<ItemBean>
        get() = mItems

    override fun onStart() {
        super.onStart()
        file = arguments?.serializableCompat<File>("file")
    }

    private fun export(delete: Boolean) {
        //todo new function compact
        Globals.mainScope.launchOnThread {
            logd { "export file: $file" }
            exportInThread(file!!, delete)
        }
    }

    @SuppressLint("SdCardPath")
    private suspend fun exportInThread(file: File, delete: Boolean) {
        delay(0)

        //val info = exportFileToDownload(file.name , file)
        val uri = ignoreError {
            saveFileToPublicDirectory(
                Globals.app, file, delete,
                "MyDroidTransfer"
            )
        }

        if (delete) {
            refreshFileListCallback?.get()?.invoke()
        }

        val util = if(uri != null) UriParserUtil(uri) else null
        util?.parseSuspend(Globals.app.contentResolver)
        val parsedInfo = util?.parsedInfo
        if (parsedInfo != null) {
            val fullPath = parsedInfo.fullPath
            val relativePath = parsedInfo.relativePath
            if (!fullPath.isNullOrEmpty()) {
                val firstStr = String.format(R.string.save_to_success.resStr(), fullPath)
                fileExportSuccessCallback?.get()?.invoke(firstStr, fullPath.replace("/storage/emulated/0/", "/sdcard/"))
            } else if (!relativePath.isNullOrEmpty()) {
                val p = relativePath.replace("/storage/emulated/0/", "/sdcard/")
                fileExportSuccessCallback?.get()?.invoke(relativePath, "/sdcard/$p")
            }
        } else {
            fileExportFailCallback?.get()?.invoke(R.string.save_failed.resStr())
        }
    }

    override fun notify(tag: Any) {
        when (tag.toString()) {
        }
    }
}