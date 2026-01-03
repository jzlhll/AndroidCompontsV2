package com.au.module_android.clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils

/**
 * 注册监听板
 */
class ClipBoardHelp {

    private var mClipboardManager: ClipboardManager? = null

    fun getClipBoardContent(activity: Activity): String? {
        if (mClipboardManager == null) {
            mClipboardManager =
                activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        try {
            if (mClipboardManager!!.hasPrimaryClip()) {
                val primaryClip = mClipboardManager!!.primaryClip
                if (primaryClip != null && primaryClip.itemCount > 0) {
                    val content = primaryClip.getItemAt(0).text.toString()
                    if (!TextUtils.isEmpty(content)) {
                        return content
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun copyToClipBoard(activity: Activity, content: String?) {
        if (mClipboardManager == null) {
            mClipboardManager =
                activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        try {
            val clipData = ClipData.newPlainText("", content)
            mClipboardManager!!.setPrimaryClip(clipData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addClipBoardChaneListener(
        activity: Activity,
        onClipBoardChangedListener: OnClipBoardChangedListener?
    ) {
        if (mClipboardManager == null) {
            mClipboardManager =
                activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        mClipboardManager!!.addPrimaryClipChangedListener {
            try {
                if (mClipboardManager!!.hasPrimaryClip()) {
                    val primaryClip = mClipboardManager!!.primaryClip
                    if (primaryClip != null && primaryClip.itemCount > 0) {
                        val content = primaryClip.getItemAt(0).text.toString()
                        if (!TextUtils.isEmpty(content)) {
                            onClipBoardChangedListener?.onChanged(content)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

interface OnClipBoardChangedListener {
    fun onChanged(text: String?)
}