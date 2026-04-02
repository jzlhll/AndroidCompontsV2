package com.au.module_androidui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.IntRange
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.au.module_android.utils.*
import com.au.module_androidui.R
import com.au.module_androidui.ui.base.AbsBottomDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.min

/**
 * FragmentBottomSheetDialog
 * @param hasEditText 是否有输入框，有输入框，则会安排弹窗的方式有变。
 */
class FragmentBottomSheetDialog(hasEditText:Boolean = false) : AbsBottomDialog(hasEditText) {
    companion object {
        /**
         * @param manager 基于哪个fragment的childFragmentManger而弹出。
         * @param fgBundle 创建的内容Fragment携带了arguments
         * @param height 取值范围为0~Int.MAX_VALUE；0时按内容自适应高度，大于0时尽量以给定高度为准，但最大不会超过screenHeight-statusBarHeight，传Int.MAX_VALUE可占满该最大值
         * @param paddingMode 是否需要padding模式, 指的是没有横杠，左右两边有padding
         * @param showHeadLine 是否需要显示headLine
         */
        inline fun <reified T : Fragment> show(
            manager: FragmentManager,
            fgBundle: Bundle? = null,
            @IntRange(from = 0)
            height: Int = 0,
            paddingMode:Boolean = false,
            hasEditText: Boolean = false,
            canCancel:Boolean = true,
            showHeadLine: Boolean = true,
        ): FragmentBottomSheetDialog {
            if (height < 0) {
                throw IllegalArgumentException("height must be >= 0")
            }
            val dialog = FragmentBottomSheetDialog(hasEditText)
            dialog.arguments = Bundle().also {
                it.putSerializable("fgClass", T::class.java)
                it.putBoolean("paddingMode", paddingMode)
                if (fgBundle != null) {
                    it.putBundle("fgBundle", fgBundle)
                }
                it.putInt("height", height)
                it.putBoolean("canCancel", canCancel)
                it.putBoolean("showHeadLine", showHeadLine)
            }
            dialog.show(manager, "FragmentContainBottomSheetDialog")
            return dialog
        }
    }

    private val fgClass by unsafeLazy {
        arguments?.serializableCompat<Class<Fragment>>("fgClass")
    }
    private val fgBundle by unsafeLazy { arguments?.getBundle("fgBundle") }
    private val height by unsafeLazy { arguments?.getInt("height") ?: 0 }

    private val fragment by unsafeLazy { fgClass?.getDeclaredConstructor()?.newInstance() }
    private val canCancel by unsafeLazy { arguments?.getBoolean("canCancel") ?: true }

    private val paddingMode by unsafeLazy { arguments?.getBoolean("paddingMode", false) ?: false}

    private val showHeadLine by unsafeLazy { arguments?.getBoolean("showHeadLine", true) ?: true}

    override fun onCreatingView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val layoutId = if (paddingMode) { R.layout.dialog_bottomsheet_padding } else { R.layout.dialog_bottomsheet }
        val root = inflater.inflate(layoutId, container, false)
        val fcv = root.findViewById<FragmentContainerView>(R.id.fcv)

        val headLine = root.findViewById<View>(R.id.headLine)
        headLine?.visibility = if (showHeadLine) View.VISIBLE else View.GONE

        val fragment = this.fragment
        if (fragment != null) {
            //最大高度限定实现
            val screenSize = requireActivity().getScreenFullSize()
            val staAndNavHeight = requireActivity().currentStatusBarAndNavBarHeight() //在dialog中使用。已经渲染好了。这肯定是ok了的。
            val statusBarHeight = staAndNavHeight?.first ?: 0
            val navigationBarHeight = staAndNavHeight?.second ?: 0

            val maxHeight:Int = screenSize.second - statusBarHeight
            val height = this.height

            if (height == 0) {
                // behavior.maxHeight 只限制最大可达高度，实际仍会按内容高度自适应显示。
                dialog.asOrNull<BottomSheetDialog>()?.behavior?.let { behavior->
                    behavior.maxHeight = maxHeight
                }
            } else {
                // else分支约定优先使用传入高度，但最终会限制在最大全屏高度screenHeight-statusBarHeight以内。
                // 转成Long后再相加，避免传Int.MAX_VALUE时叠加navigationBarHeight发生溢出。
                val targetHeight = height.toLong() + (if (isPaddingNavigationBarHeight) navigationBarHeight.toLong() else 0L)
                val fixHeight = min(targetHeight, maxHeight.toLong()).toInt()
                root.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, fixHeight)
            }

            fgBundle?.let {
                fragment.arguments = it
            }

            childFragmentManager.beginTransaction().also {
                it.replace(fcv.id, fragment, null)
                it.commitNow()
            }
        } else {
            dismiss()
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        if (!canCancel) {
            setCancelable(false)
        }
    }
}