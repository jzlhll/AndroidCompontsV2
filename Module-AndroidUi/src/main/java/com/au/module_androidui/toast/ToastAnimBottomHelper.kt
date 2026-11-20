package com.au.module_androidui.toast

import android.os.SystemClock
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.au.module_android.Globals
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.invisible
import com.au.module_android.utils.visible
import com.au.module_androidui.R
import com.au.module_androidui.databinding.LayoutToastBottomBinding
import com.au.module_androidui.widget.CustomFontText
import java.util.concurrent.atomic.AtomicLong

internal class ToastAnimBottomHelper : IToastAnim() {
    private val toastIndex = AtomicLong(1)

    private val toastAlreadyShownList = ArrayList<View?>()

    private val dismissBottomToast = Runnable { dismissToast() }

    override fun createToastBindingInner(parent: ViewGroup,
                                         duration: Long,
                                         textLen:Int,
                                         isAlwaysShown:Boolean) : LayoutToastBottomBinding {
        return createToastBinding(parent, duration, isAlwaysShown, R.layout.layout_toast_bottom)
    }

    // 两种布局保留相同的控件 ID 和类型，共用展示与消失动画。
    @Synchronized
    private fun createToastBinding(
        parent: ViewGroup,
        duration: Long,
        isAlwaysShown: Boolean,
        @LayoutRes layoutResId: Int,
    ): LayoutToastBottomBinding {
        val binding = LayoutToastBottomBinding.bind(
            LayoutInflater.from(parent.context).inflate(layoutResId, parent, false),
        )
        parent.addView(binding.root)
        val toast = binding.root
        toast.tag = toastIndex.addAndGet(1)
        toast.invisible()
        // 先登记可关闭的视图，避免自动关闭早于入场动画结束时漏掉当前提示。
        synchronized(toastAlreadyShownList) {
            toastAlreadyShownList.add(toast)
        }
        toast.post {
            toast.alpha = 0f
            toast.visible()
            showAnim(toast)
        }
        Globals.mainHandler.removeCallbacks(dismissBottomToast)
        if (!isAlwaysShown) {
            Globals.mainHandler.postDelayed(dismissBottomToast, duration)
        }

        return binding
    }

    private fun dismissToast(toast:View?, directly:Boolean) {
        toast ?: return
        toast.findViewById<View>(R.id.toastActionTv)?.isEnabled = false
        val message = toast.getTag(R.id.text)?.toString().orEmpty()
        if (directly) {
            toast.parent?.asOrNull<ViewGroup>()?.removeView(toast)
            clearLastToastTs(message)
        } else {
            toast.parent?.asOrNull<ViewGroup>()?.let {
                toast.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        it.removeView(toast)
                        clearLastToastTs(message)
                    }
                    .start()
            }
        }
    }

//    private fun dismissToastByTag(tag:Any?) {
//        if (tag == null) return
//        synchronized(toastAlreadyShownList) {
//            var sz = toastAlreadyShownList.count()
//            while (sz-- > 0) {
//                val toast = toastAlreadyShownList[sz]
//                if (toast != null && toast.tag == tag) {
//                    dismissToast(toast, false)
//                    toastAlreadyShownList[sz] = null
//                    break
//                }
//            }
//
//            toastAlreadyShownList.removeAll { it == null }
//        }
//    }

    private fun dismissToast(directlyRemove:Boolean = false, clearCurrent:Boolean = true) {
        synchronized(toastAlreadyShownList) {
            var sz = toastAlreadyShownList.count()
            while (sz-- > 0) {
                val toast = toastAlreadyShownList[sz]
                if (clearCurrent) {
                    dismissToast(toast, directlyRemove)
                    toastAlreadyShownList[sz] = null
                } else if (toast?.tag != toastIndex.get()) {
                    dismissToast(toast, directlyRemove)
                    toastAlreadyShownList[sz] = null
                }
            }

            toastAlreadyShownList.removeAll { it == null }
        }
    }

    private fun showAnim(toast: ViewGroup) {
        toast.animate()
            .alpha(1f)
            .setDuration(250)
            .withEndAction {
                dismissToast(directlyRemove = true, clearCurrent = false)
            }
            .start()
    }

    fun toast(parent: ViewGroup,
              duration: Long,
              spanned: Spanned?,
              iconResId: Int?,
              iconOnRight: Boolean,
              isAlwaysShown: Boolean,
              actionText: String? = null,
              onAction: (() -> Unit)? = null) : ViewBinding? {
        if (!checkToastCanShow(spanned, parent, allowsRepeatedMessage = onAction != null)) {
            return null
        }

        synchronized(toastAlreadyShownList) {
            toastAlreadyShownList.indices.forEach { index ->
                val previous = toastAlreadyShownList[index] ?: return@forEach
                val action = previous.findViewById<View>(R.id.toastActionTv) ?: return@forEach
                action.isEnabled = false
                dismissToast(previous, true)
                toastAlreadyShownList[index] = null
            }
            toastAlreadyShownList.removeAll { it == null }
        }

        val layoutResId = if (onAction != null) {
            R.layout.layout_toast_bottom_action
        } else if (iconOnRight) {
            R.layout.layout_toast_bottom_icon_right
        } else {
            R.layout.layout_toast_bottom
        }
        val binding = createToastBinding(parent, duration, isAlwaysShown, layoutResId)
        binding.root.setTag(R.id.text, spanned.toString())

        binding.text.text = spanned

        if (onAction != null) {
            val action = binding.root.findViewById<CustomFontText>(R.id.toastActionTv)
            val deadline = SystemClock.uptimeMillis() + duration
            action.text = actionText
            action.setOnClickListener {
                if (action.isEnabled && binding.root.isAttachedToWindow && SystemClock.uptimeMillis() < deadline) {
                    action.isEnabled = false
                    synchronized(toastAlreadyShownList) {
                        toastAlreadyShownList.remove(binding.root)
                    }
                    dismissToast(binding.root, true)
                    onAction()
                }
            }
        }

        if (iconResId != null && iconResId != 0) {
            binding.icon.visibility = View.VISIBLE
            binding.icon.setImageResource(iconResId)
        } else {
            binding.icon.visibility = View.GONE
        }

        return binding
    }
}
