package com.allan.mydroid.views.receiver

import android.annotation.SuppressLint
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.allan.mydroid.BuildConfig
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.serializableCompat
import com.au.module_android.utilsmedia.myParseSuspend
import com.au.module_android.utilsmedia.openWith
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_android.utilsmedia.shareFile
import com.au.module_androidui.dialogs.AbsActionDialogFragment
import com.au.module_androidui.dialogs.FragmentBottomSheetDialog
import kotlinx.coroutines.delay
import java.io.File
import java.lang.ref.WeakReference


class ReceiveHolderActionDialog : AbsActionDialogFragment() {
    companion object Companion {
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
                file:File,
                fileExportSuccessCallback:(info:String, exportFileStr:String)->Unit = {_,_->},
                fileExportFailCallback:(String)->Unit = {},
                refreshFileListCallback:()->Unit = {},
                importSendCallback:()->Unit = {}) {
            FragmentBottomSheetDialog.show<ReceiveHolderActionDialog>(manager, bundleOf("file" to file))
            this.fileExportSuccessCallback = WeakReference(fileExportSuccessCallback)
            this.fileExportFailCallback = WeakReference(fileExportFailCallback)
            this.refreshFileListCallback = WeakReference(refreshFileListCallback)
            this.importSendCallback = WeakReference(importSendCallback)
        }

        private const val TAG_OPEN = "open"
        private const val TAG_SHARE = "share"
        private const val TAG_DELETE = "delete"
        private const val TAG_EXPORT_ONLY = "exportOnly"
        private const val TAG_EXPORT_AND_DEL = "exportAndDelete"
    }

    val mItems = listOf(
        ItemBean(TAG_OPEN, R.string.open.resStr(), R.drawable.ic_open, normalColor),
        ItemBean(TAG_SHARE, R.string.share.resStr(), R.drawable.ic_share, normalColor),
        ItemBean(TAG_EXPORT_ONLY, R.string.export.resStr(), R.drawable.ic_export, normalColor),
        ItemBean(TAG_DELETE, R.string.delete.resStr(), R.drawable.ic_delete, normalColor),
        ItemBean(TAG_EXPORT_AND_DEL, R.string.export_and_delete.resStr(), R.drawable.ic_export_and_delete, normalColor))

    override val items: List<ItemBean>
        get() = mItems

    private lateinit var file: File

    override fun onStart() {
        super.onStart()
        file = arguments?.serializableCompat<File>("file")!!
    }

    private fun export(delete: Boolean) {
        Globals.mainScope.launchOnThread {
            exportInThread(file, delete)
        }
    }

    @SuppressLint("SdCardPath")
    private suspend fun exportInThread(file: File, delete: Boolean) {
        delay(0)

        val uri = ignoreError {
            saveFileToPublicDirectory(
                Globals.app,
                file,
                delete,
                "MyDroidTransfer"
            )
        }

        if (delete) {
            refreshFileListCallback?.get()?.invoke()
        }

        val parsedInfo = uri?.myParseSuspend(Globals.app.contentResolver)
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
            TAG_OPEN -> {
                openWith(requireActivity(),
                    file,
                    BuildConfig.APPLICATION_ID,
                    getString(R.string.open))
            }
            TAG_SHARE -> {
                shareFile(Globals.app, file, getString(R.string.share))
            }
            TAG_EXPORT_ONLY -> {
                export(false)
            }
            TAG_EXPORT_AND_DEL -> {
                export(true)
            }
            TAG_DELETE -> {
                Globals.mainScope.launchOnThread {
                    ignoreError { file.delete() }
                    delay(100)
                    refreshFileListCallback?.get()?.invoke()
                }
            }
        }
    }
}