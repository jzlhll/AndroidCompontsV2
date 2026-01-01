package com.au.module_androidui.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import com.au.module_android.click.onClick
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.getScreenFullSize
import com.au.module_androidui.R
import com.au.module_androidui.databinding.AbsCenterFakeDialogBinding
import com.au.module_androidui.ui.createViewBinding

/**
 * 定义一种弹窗，用于输入内容
 * #60000000的颜色覆盖，就跟系统的 overlay 差不多(亮色和黑暗模式都通用支持)
 * 模拟的动画和背景。但是其实是直接添加在 Activity DecorView上。
 *
 * ```xml
 * <com.au.module_androidui.widget.BgBuildConstraintLayout
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:app="http://schemas.android.com/apk/res-auto"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     app:backgroundNormal="@color/windowDialogBackground"
 *     app:cornerSizeTopLeft="12dp"
 *     app:cornerSizeTopRight="12dp"
 *     app:cornerSizeBottomLeft="12dp"
 *     app:cornerSizeBottomRight="12dp">
 *
 * </com.au.module_androidui.widget.BgBuildConstraintLayout>
 * ```
 */
abstract class AbsCenterFakeDialog<T : ViewBinding> : DefaultLifecycleObserver {
    /**
     * 用来显示的背景
     */
    private lateinit var absBinding : AbsCenterFakeDialogBinding
    private lateinit var mContentBinding: T

    private var isInited = false

    /**
     * 每次显示时调用，设置内容文字等参数
     */
    abstract fun onShow(activity: ComponentActivity, binding: T)

    /**
     * 0～3的取值。
     * 是否使用 markup 来占据一定的高度，把 contentView 往上挤一点。
     * 如果是 0，就是不使用 markup。如果是 1 就使用屏幕的高度的 1/20, 2就是1/10，3就是 3/20。
     *
     */
    open fun markupShow(): Int = 0

    private fun getMarkupHeight(activity: Activity) : Int{
        val per20 = activity.getScreenFullSize().second / 20
        return when(markupShow()) {
            1 -> per20
            2 -> per20 * 2
            3 -> per20 * 3
            else -> 0
        }
    }

    private val bgShowAnim = R.anim.activity_alpha_in
    private val bgHideAnim = R.anim.activity_alpha_out
    private val contentShowAnim = R.anim.dialog_scale_in
    private val contentHideAnim = R.anim.dialog_scale_out

    fun hide() {
        val bgAnimation = AnimationUtils.loadAnimation(absBinding.root.context, bgHideAnim)
        absBinding.root.startAnimation(bgAnimation)

        val contentView = mContentBinding.root
        val contentAnimation = AnimationUtils.loadAnimation(contentView.context, contentHideAnim)
        contentAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                absBinding.root.parent?.asOrNull<ViewGroup>()?.removeView(absBinding.root)
            }
        })
        contentView.startAnimation(contentAnimation)

        onHide(mContentBinding)
    }

    /**
     * 子类实现。比如有键盘就可以调用隐藏
     */
    abstract fun onHide(binding: T)

    override fun onStop(owner: LifecycleOwner) { //对于任何弹窗类都可以直接销毁
        super.onStop(owner)
        hide()
    }

    fun pop(fragment: Fragment) {
        pop(fragment.requireActivity())
    }

    open fun createBinding(activity: Activity) : T{
        return createViewBinding(this@AbsCenterFakeDialog.javaClass, activity.layoutInflater, null, false)
    }

    fun pop(activity: ComponentActivity) {
        if (!isInited) {
            isInited = true
            activity.lifecycle.addObserver(this)

            absBinding = AbsCenterFakeDialogBinding.inflate(activity.layoutInflater).apply {
                absBinding = this
                val cb = createBinding(activity)
                mContentBinding = cb
                this.root.addView(cb.root, 0)

                val markupHeight = getMarkupHeight(activity)
                if (markupHeight > 0) {
                    absBinding.markup.layoutParams = absBinding.markup.layoutParams.apply {
                        height = markupHeight
                    }
                }

                this.root.onClick {
                    hide()
                }
            }
        }

        if (absBinding.root.parent == null) {
            activity.window.decorView.asOrNull<FrameLayout>()?.apply {
                addView(absBinding.root)
                // 背景添加alpha进入动画
                val bgAnimation = AnimationUtils.loadAnimation(activity, bgShowAnim)
                absBinding.root.startAnimation(bgAnimation)
                // 内容添加缩放进入动画
                val contentView = mContentBinding.root
                val contentAnimation = AnimationUtils.loadAnimation(activity, contentShowAnim)
                contentView.startAnimation(contentAnimation)
            }
        }

        onShow(activity, mContentBinding)
    }

}
