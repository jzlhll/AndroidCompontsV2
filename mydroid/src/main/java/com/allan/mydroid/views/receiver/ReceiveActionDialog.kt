package com.allan.mydroid.views.receiver

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.allan.mydroid.R
import com.au.module_android.Globals
import com.au.module_android.Globals.resStr
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_android.utils.serializableCompat
import com.au.module_android.utilsmedia.ContentUriRealPathType
import com.au.module_android.utilsmedia.getRealPath
import com.au.module_android.utilsmedia.saveFileToPublicDirectory
import com.au.module_android.utilsmedia.shareFile
import com.au.module_androidui.dialogs.AbsActionDialogFragment
import com.au.module_androidui.dialogs.FragmentBottomSheetDialog
import kotlinx.coroutines.delay
import java.io.File
import java.lang.ref.WeakReference

class ReceiveActionDialog(private var file: File? = null) : AbsActionDialogFragment() {
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
            FragmentBottomSheetDialog.show<ReceiveActionDialog>(manager, bundleOf("file" to file))
            this.fileExportSuccessCallback = WeakReference(fileExportSuccessCallback)
            this.fileExportFailCallback = WeakReference(fileExportFailCallback)
            this.refreshFileListCallback = WeakReference(refreshFileListCallback)
            this.importSendCallback = WeakReference(importSendCallback)
        }

        private const val TAG_SHARE = "share"
        private const val TAG_DELETE = "delete"
        private const val TAG_EXPORT_ONLY = "exportOnly"
        private const val TAG_EXPORT_AND_DEL = "exportAndDelete"
    }

    val normalColor = ColorStateList.valueOf(Globals.getColor(com.au.module_androidcolor.R.color.color_text_normal))

    val mItems = listOf(
        ItemBean(TAG_SHARE, R.string.share.resStr(), R.drawable.ic_share, normalColor),
        ItemBean(TAG_DELETE, R.string.delete.resStr(), R.drawable.ic_delete, normalColor),
        ItemBean(TAG_EXPORT_ONLY, R.string.export.resStr(), R.drawable.ic_export, normalColor),
        ItemBean(TAG_EXPORT_AND_DEL, R.string.export_and_delete.resStr(), R.drawable.ic_export_and_delete, normalColor))
    override val items: List<ItemBean>
        get() = mItems

    override fun onStart() {
        super.onStart()
        file = arguments?.serializableCompat<File>("file")
    }

    private fun export(delete: Boolean) {
        Globals.mainScope.launchOnThread {
            logd { "export file: $file" }
            exportInThread(file!!, delete)
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

        val pair = uri?.getRealPath(Globals.app)
        if (pair != null) {
            val p = pair.first.replace("/storage/emulated/0/", "/sdcard/")
            val firstStr = String.format(R.string.save_to_success.resStr(), pair.first)
            when (pair.second) {
                ContentUriRealPathType.RelativePath -> {
                    fileExportSuccessCallback?.get()?.invoke(firstStr, "/sdcard/$p")
                }
                ContentUriRealPathType.FullPath -> {
                    fileExportSuccessCallback?.get()?.invoke(firstStr, p)
                }
            }
        } else {
            fileExportFailCallback?.get()?.invoke(R.string.save_failed.resStr())
        }
    }

    override fun notify(tag: Any) {
        when (tag.toString()) {
            TAG_SHARE -> {
                shareFile(Globals.app, file)
            }
            TAG_EXPORT_ONLY -> {
                export(false)
            }
            TAG_EXPORT_AND_DEL -> {
                export(true)
            }
            TAG_DELETE -> {
                Globals.mainScope.launchOnThread {
                    ignoreError { file?.delete() }
                    delay(100)
                    refreshFileListCallback?.get()?.invoke()
                }
            }
        }
    }
}