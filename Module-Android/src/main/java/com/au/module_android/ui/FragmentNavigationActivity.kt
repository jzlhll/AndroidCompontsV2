package com.au.module_android.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import com.au.module_android.BuildConfig
import com.au.module_android.ui.FragmentShellActivity.Companion.KEY_ENTER_ANIM
import com.au.module_android.ui.FragmentShellActivity.Companion.KEY_EXIT_ANIM
import com.au.module_android.ui.FragmentShellActivity.Companion.KEY_FRAGMENT_ARGUMENTS
import com.au.module_android.ui.base.AbsFragment
import com.au.module_android.ui.base.IFullWindow
import com.au.module_android.ui.base.ImmersiveMode
import com.au.module_android.ui.navigation.FragmentNavigationPage
import com.au.module_android.ui.navigation.FragmentNavigationScene
import com.au.module_android.ui.navigation.FragmentNavigationViewModel
import com.au.module_android.ui.views.ViewActivity
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.unsafeLazy

/**
 * @author au
 * Date: 2023/8/29
 * Description 限制
 */
open class FragmentNavigationActivity : ViewActivity() {
    private val mEnterAnim by unsafeLazy { intent.getIntExtra(KEY_ENTER_ANIM, 0) }
    private val mExitAnim by unsafeLazy { intent.getIntExtra(KEY_EXIT_ANIM, 0) }

    lateinit var fragmentNavigationScene : FragmentNavigationScene

    override val exitAnim: Int?
        get() = mExitAnim

    override val enterAnim: Int?
        get() = mEnterAnim

    val viewModel by unsafeLazy { ViewModelProvider(this)[FragmentNavigationViewModel::class.java] }

    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = FragmentContainerView(inflater.context)
        v.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        v.id = View.generateViewId()
        val instance = fragmentClass.getDeclaredConstructor().newInstance()
        instance.arguments = intent.getBundleExtra(KEY_FRAGMENT_ARGUMENTS)

        mIsAutoHideIme = instance.asOrNull<AbsFragment>()?.isAutoHideIme() ?: false

        if (BuildConfig.DEBUG) {
            Log.d("AU_APP", "FragmentShellActivity: ${fragmentClass.name}")
        }

        //1️⃣。
        // 作为容器，我们将immersiveMode()返回FullImmersive，得到的结果就是activity完全沉浸。
        //至于padding交给Fragment处理。
        if (instance is IFullWindow) {
            instance.postPaddingRootInner(this, v)
        }

        supportFragmentManager.beginTransaction().also {
            it.replace(v.id, instance)
            it.commit()
        }//todo 增加tag。
        return v
    }

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        AndroidBug5497Workaround.assistActivity(this)
//    }

    final override fun immersiveMode(): ImmersiveMode { //默认全沉浸，因为配合实现沉浸式1️⃣。
        return ImmersiveMode.FullImmersive
    }

    private var mIsAutoHideIme = false

    final override fun isAutoHideIme(): Boolean {
        return mIsAutoHideIme
    }

    fun navigateTo(page: FragmentNavigationPage, clear: Boolean = false) {
        //current.save()
        //todo
    }
}