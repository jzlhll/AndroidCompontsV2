package com.au.module_androidui.toast

import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.invisible
import com.au.module_android.utils.visible
import com.au.module_androidui.R
import com.au.module_androidui.databinding.LayoutToastTopBinding
import java.util.concurrent.atomic.AtomicLong

internal class ToastAnimTopHelper : IToastAnim() {
    private val toastIndex = AtomicLong(1)

    /**
     * 已经上屏的Toast list。注：不包括永远显示的，永远显示的需要自行管理消除。
     */
    private val toastAlreadyShownList = ArrayList<View?>()

    private val dismissTopToast = Runnable { dismissToast() }

    //isAlwaysShown表示是否常驻，则没有delay关闭的事情。则需要自行根据返回值，自行关闭，本类不做管理。
    //这里是创建布局，用于layout共享了icon，所以这里不需要icon
    @Synchronized
    override fun createToastBindingInner(
        parent: ViewGroup,
        duration: Long,
        textLen: Int,
        isAlwaysShown: Boolean
    ) : LayoutToastTopBinding {
        val binding = LayoutToastTopBinding.inflate(LayoutInflater.from(parent.context), parent, true)
        val toast = binding.root
        toast.tag = toastIndex.addAndGet(1)
        toast.invisible()
        toast.post {
            toast.translationY = -toast.height.toFloat()
            toast.visible()
            showAnim(toast)
        }
        Globals.mainHandler.removeCallbacks(dismissTopToast)
        if (!isAlwaysShown) {
            Globals.mainHandler.postDelayed(dismissTopToast, duration)
        }

        return binding
    }

    /**
     * 消除一个toast。是否直接清除或者动画退场。
     */
    private fun dismissToast(toast:View?, directly:Boolean) {
        toast ?: return
        val message = toast.getTag(R.id.text)?.toString().orEmpty()
        if (directly) {
            toast.parent?.asOrNull<ViewGroup>()?.removeView(toast)
            clearLastToastTs(message)
        } else {
            toast.parent?.asOrNull<ViewGroup>()?.let {
                toast.animate()
                    .translationY(-toast.height.toFloat())
                    .setDuration(200)
                    .withEndAction {
                        it.removeView(toast)
                        clearLastToastTs(message)
                    }
                    .start()
            }
        }
    }

    private fun dismissToastByTag(tag:Any?) {
        if (tag == null) return
        synchronized(toastAlreadyShownList) {
            var sz = toastAlreadyShownList.count()
            while (sz-- > 0) {
                val toast = toastAlreadyShownList[sz]
                if (toast != null && toast.tag == tag) {
                    dismissToast(toast, false)
                    toastAlreadyShownList[sz] = null
                    break
                }
            }

            toastAlreadyShownList.removeAll { it == null }
        }
    }

    /**
     * 取消toast
     */
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
            .translationY(0f)
            .setDuration(250)
            .withEndAction {
                //当到位了以后才添加到列表。
                synchronized(toastAlreadyShownList) {
                    toastAlreadyShownList.add(toast)
                }

                //到位后直接清理掉非当前的。
                dismissToast(directlyRemove = true, clearCurrent = false)
            }
            .start()
    }

    fun toast(parent: ViewGroup,
                           duration: Long,
                           spanned: Spanned?,
                           btnShow: Boolean,
                           btnTextStrId:Int,
                           btnColorResId:Int,
                           replaceCloseIconIsClose:Boolean = true,
                           replaceCloseIconResId:Int? = null,
                           isAlwaysShown: Boolean) : ViewBinding? {
        if (!checkToastCanShow(spanned, parent)) {
            return null
        }

        val maxTextLen = spanned?.length ?: 0
        val binding = createToastBindingInner(parent, duration, maxTextLen, isAlwaysShown = isAlwaysShown)
        val tag = binding.root.tag
        binding.root.setTag(R.id.text, spanned.toString())

        binding.text.text = spanned

        if (btnShow) {
            binding.textBtn.visibility = View.VISIBLE
            binding.textBtn.setText(btnTextStrId)
            binding.textBtn.setTextColor(Globals.getColor(btnColorResId))
        } else {
            binding.textBtn.visibility = View.GONE
        }

        if (replaceCloseIconResId != null) {
            binding.closeBtn.imageTintList = null
            binding.closeBtn.setImageResource(replaceCloseIconResId)
        }

        if (replaceCloseIconIsClose) {
            binding.closeBtn.onClick {
                dismissToastByTag(tag)
            }
        }
        return binding
    }
}
