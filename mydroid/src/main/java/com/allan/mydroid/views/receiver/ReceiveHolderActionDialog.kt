package com.allan.mydroid.views.receiver

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import com.allan.mydroid.BuildConfig
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.au.module_android.utils.serializableCompat
import com.au.module_android.utilsmedia.myParseSuspend
import com.au.module_android.utilsmedia.openWith
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_android.utilsmedia.shareFile
import com.au.module_androidui.dialogs.AbsActionDialogFragment
import com.au.module_androidui.dialogs.FragmentBottomSheetDialog
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
            this.fileExportSuccessCallback = WeakReference(fileExportSuccessCallback)
            this.fileExportFailCallback = WeakReference(fileExportFailCallback)
            this.refreshFileListCallback = WeakReference(refreshFileListCallback)
            this.importSendCallback = WeakReference(importSendCallback)
            FragmentBottomSheetDialog.show<ReceiveHolderActionDialog>(manager, Bundle().also {
                it.putSerializable("file", file)
            })
        }

        private const val TAG_OPEN = "open"
        private const val TAG_SHARE = "share"
        private const val TAG_DELETE = "delete"
        private const val TAG_EXPORT_ONLY = "exportOnly"
        private const val TAG_EXPORT_AND_DEL = "exportAndDelete"
    }

    override val items: List<ItemBean> by lazy {
        listOf(
            ItemBean(TAG_OPEN, getString(R.string.open), R.drawable.ic_open, normalColor),
            ItemBean(TAG_SHARE, getString(R.string.share), R.drawable.ic_share, normalColor),
            ItemBean(TAG_EXPORT_ONLY, getString(R.string.export), R.drawable.ic_export, normalColor),
            ItemBean(TAG_DELETE, getString(R.string.delete), R.drawable.ic_delete, normalColor),
            ItemBean(TAG_EXPORT_AND_DEL, getString(R.string.export_and_delete), R.drawable.ic_export_and_delete, normalColor)
        )
    }

    private var file: File? = null

    override fun onStart() {
        super.onStart()
        file = arguments?.serializableCompat<File>("file")
    }

    private fun export(delete: Boolean) {
        val target = file ?: return
        val success = fileExportSuccessCallback?.get()
        val failure = fileExportFailCallback?.get()
        val refresh = refreshFileListCallback?.get()
        Globals.mainScope.launch {
            try {
                val result = withIOThread { exportInThread(target, delete) }
                if (delete) refresh?.invoke()
                if (result == null) failure?.invoke(Globals.getString(R.string.save_failed))
                else success?.invoke(result.first, result.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e) { "Export received file failed" }
                failure?.invoke(Globals.getString(R.string.save_failed))
            }
        }
    }

    @SuppressLint("SdCardPath")
    private suspend fun exportInThread(file: File, delete: Boolean): Pair<String, String>? {
        val uri = saveFileToPublicDirectory(Globals.app, file, delete, "MyDroidTransfer") ?: return null
        val parsedInfo = uri.myParseSuspend(Globals.app.contentResolver)
        val path = parsedInfo.fullPath?.takeIf { it.isNotEmpty() }
            ?: parsedInfo.relativePath?.takeIf { it.isNotEmpty() }?.let { "/sdcard/$it" }
            ?: uri.toString()
        val displayPath = path.replace("/storage/emulated/0/", "/sdcard/")
        return String.format(Globals.getString(R.string.save_to_success), displayPath) to displayPath
    }

    override fun notify(tag: Any) {
        val file = this.file ?: return

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
                val refresh = refreshFileListCallback?.get()
                val failure = fileExportFailCallback?.get()
                Globals.mainScope.launch {
                    try {
                        withIOThread { check(file.delete()) { "Cannot delete received file" } }
                        refresh?.invoke()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logEx(throwable = e) { "Delete received file failed" }
                        failure?.invoke(Globals.getString(R.string.something_error))
                    }
                }
            }
        }
    }
}