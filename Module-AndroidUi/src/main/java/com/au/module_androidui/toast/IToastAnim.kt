package com.au.module_androidui.toast

import android.text.Spanned
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

internal abstract class IToastAnim {
    private var lastMessage: String? = null
    private var lastToastTs: Long = 0L

    private fun tryShowMessage(message: String): Boolean {
        synchronized(this) {
            if (message == lastMessage && lastToastTs != 0L) {
                return false
            }
            lastMessage = message
            lastToastTs = System.currentTimeMillis()
            return true
        }
    }

    protected fun clearLastToastTs(message: String) {
        synchronized(this) {
            if (lastMessage == message) {
                lastToastTs = 0L
            }
        }
    }

    protected fun checkToastCanShow(spanned: Spanned?, parent: ViewGroup?, allowsRepeatedMessage: Boolean = false): Boolean {
        val message = spanned?.toString().orEmpty()
        if (message.isEmpty() || (!allowsRepeatedMessage && !tryShowMessage(message))) {
            return false
        }
        if (parent == null || !parent.isAttachedToWindow) {
            clearLastToastTs(message)
            return false
        }
        return true
    }

    abstract fun createToastBindingInner(
        parent: ViewGroup,
        duration: Long,
        textLen: Int,
        isAlwaysShown: Boolean = false,
    ): ViewBinding
}
