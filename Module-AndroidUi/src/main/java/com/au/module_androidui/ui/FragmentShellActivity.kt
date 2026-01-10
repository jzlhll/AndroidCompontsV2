package com.au.module_androidui.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.au.module_android.BuildConfig
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.currentStatusBarAndNavBarHeight
import com.au.module_android.utils.serializableExtraCompat
import com.au.module_android.utils.unsafeLazy
import com.au.module_androidui.R
import com.au.module_androidui.ui.base.AbsFragment
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.views.ViewActivity
import com.au.module_simplepermission.activity.ActivityForResult
import org.koin.android.ext.android.get
import org.koin.core.qualifier.named

/**
 * @author au
 * Date: 2023/8/29
 * Description 限制
 */
open class FragmentShellActivity : ViewActivity() {
    override fun toString(): String {
        val superStr = super.toString()
        return superStr + "fragmentClass: $fragmentClass"
    }

    companion object {
        const val KEY_FRAGMENT_CLASS = "FragmentShellActivity_key_fragment"
        const val KEY_FRAGMENT_KOIN_NAME = "FragmentShellActivity_key_fragment_koin_name"
        const val KEY_FRAGMENT_ARGUMENTS = "FragmentShellActivity_key_arguments"
        const val KEY_EXIT_ANIM = "FragmentShellActivity_key_exit_anim"
        const val KEY_ENTER_ANIM = "FragmentShellActivity_key_enter_anim"

        /**
         * 把一个Fragment放到本Activity当做唯一的界面。
         *
         * @param context Context
         * @param fragmentClass 需要显示的fragment的类
         * @param arguments 用来透传给Fragment
         * @param optionsCompat 是startActivity的参数
         * @param enterAnim 与android标准不同的是，这里给出的anim都是限定即将打开的activity进入时候的动画
         * @param exitAnim  与android标准不同的是，这里给出的anim都是限定即将打开的activity退出时候的动画
         */
        fun start(context: Context,
                            fragmentClass:Class<out Fragment>,
                            arguments: Bundle? = null,
                            optionsCompat: ActivityOptionsCompat? = null,
                            enterAnim:Int? = null,
                            exitAnim:Int? = null,
                            activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
            startRoot(context, FragmentShellActivity::class.java, fragmentClass, null, arguments, optionsCompat, enterAnim, exitAnim, activityResultCallback)
        }

        /**
         * 把一个Fragment放到本Activity当做唯一的界面。
         *
         * @param context Context
         * @param fragmentClass 需要显示的fragment的类
         * @param activityResult 如果传入了非空对象，则会通过它启动，会携带返回；否则就是默认启动。
         * @param arguments 用来透传给Fragment
         * @param optionsCompat 是startActivity的参数
         * @param enterAnim 与android标准不同的是，这里给出的anim都是限定即将打开的activity进入时候的动画
         * @param exitAnim  与android标准不同的是，这里给出的anim都是限定即将打开的activity退出时候的动画
         */
        fun startForResult(context: Context,
                  fragmentClass:Class<out Fragment>,
                  activityResult:ActivityForResult,
                  arguments: Bundle? = null,
                  optionsCompat: ActivityOptionsCompat? = null,
                  enterAnim:Int? = null,
                  exitAnim:Int? = null,
                  activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
            startRoot(context, FragmentShellActivity::class.java, fragmentClass, activityResult, arguments, optionsCompat, enterAnim, exitAnim, activityResultCallback)
        }

        internal fun startRoot(context: Context,
                           showActivityClass:Class<out Activity>,
                           fragmentClass:Class<out Fragment>,
                           activityResult:ActivityForResult?,
                           arguments: Bundle?,
                           optionsCompat: ActivityOptionsCompat?,
                           enterAnim:Int? = null,
                           exitAnim:Int? = null,
                           activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
            val intent = Intent(context, showActivityClass)
            intent.putExtra(KEY_FRAGMENT_CLASS, fragmentClass)
            if (arguments != null) intent.putExtra(KEY_FRAGMENT_ARGUMENTS, arguments)
            if (exitAnim != null) intent.putExtra(KEY_EXIT_ANIM, exitAnim)
            if (enterAnim != null) intent.putExtra(KEY_ENTER_ANIM, enterAnim)

            if (activityResult != null) {
                activityResult.start(intent, optionsCompat, activityResultCallback)

                if (enterAnim != null && context is Activity) {
                    context.overridePendingTransition(enterAnim, R.anim.activity_stay)
                }
            } else {
                context.startActivityFix(intent, optionsCompat?.toBundle(), enterAnim)
            }
        }

        internal fun startRoot(context: Context,
                               showActivityClass:Class<out Activity>,
                               koinFragmentName:String,
                               activityResult:ActivityForResult?,
                               arguments: Bundle?,
                               optionsCompat: ActivityOptionsCompat?,
                               enterAnim:Int? = null,
                               exitAnim:Int? = null,
                               activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
            val intent = Intent(context, showActivityClass)
            intent.putExtra(KEY_FRAGMENT_KOIN_NAME, koinFragmentName)
            if (arguments != null) intent.putExtra(KEY_FRAGMENT_ARGUMENTS, arguments)
            if (exitAnim != null) intent.putExtra(KEY_EXIT_ANIM, exitAnim)
            if (enterAnim != null) intent.putExtra(KEY_ENTER_ANIM, enterAnim)

            if (activityResult != null) {
                activityResult.start(intent, optionsCompat, activityResultCallback)

                if (enterAnim != null && context is Activity) {
                    context.overridePendingTransition(enterAnim, R.anim.activity_stay)
                }
            } else {
                context.startActivityFix(intent, optionsCompat?.toBundle(), enterAnim)
            }
        }
    }

    val fragmentClass by unsafeLazy { intent.serializableExtraCompat<Class<Fragment>>(KEY_FRAGMENT_CLASS) }
    val fragmentKoinName by unsafeLazy { intent.getStringExtra(KEY_FRAGMENT_KOIN_NAME) }

    private val mEnterAnim by unsafeLazy { intent.getIntExtra(KEY_ENTER_ANIM, 0) }
    private val mExitAnim by unsafeLazy { intent.getIntExtra(KEY_EXIT_ANIM, 0) }

    override val exitAnim: Int?
        get() = mExitAnim

    override val enterAnim: Int?
        get() = mEnterAnim

    private var mFragment : Fragment? = null

    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = FragmentContainerView(inflater.context)
        v.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        v.id = View.generateViewId()

        val frgClz = fragmentClass
        val instance : Fragment
        if (frgClz != null) {
            instance = frgClz.getDeclaredConstructor().newInstance()
            if (BuildConfig.DEBUG) {
                Log.d("AU_APP", "FragmentShellActivity: frg ${frgClz.name}")
            }
        } else {
            instance = get<AbsFragment>(named(fragmentKoinName!!))
            if (BuildConfig.DEBUG) {
                Log.d("AU_APP", "FragmentShellActivity: koin $fragmentKoinName")
            }
        }

        mFragment = instance
        instance.arguments = intent.getBundleExtra(KEY_FRAGMENT_ARGUMENTS)

        mIsAutoHideIme = instance.asOrNull<AbsFragment>()?.isAutoHideIme() ?: false

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

    //默认沉浸式，Fragment默认不处理
    final override fun immersiveMode(): ImmersiveMode {
        return mFragment?.asOrNull<AbsFragment>()?.immersiveMode() ?: ImmersiveMode.PaddingBars
    }

    override fun onDestroy() {
        super.onDestroy()
        mFragment = null
    }

    private var mIsAutoHideIme = false

    final override fun isAutoHideIme(): Boolean {
        return mIsAutoHideIme
    }

    final override fun onWindowFocusChangedInner(hasFocus: Boolean) {
        if (hasFocus) {
            when (val immersiveMode = immersiveMode()) {
                is ImmersiveMode.PaddingBars,
                ImmersiveMode.PaddingStatusBar,
                ImmersiveMode.PaddingNavigationBar -> {
                    //普通三种将直接处理activity的界面
                    super.onWindowFocusChangedInner(true)
                }
                is ImmersiveMode.FullImmersive -> {
                    //如果是全屏沉浸式，则将处理交给fragment的代码去执行
                    val pair = currentStatusBarAndNavBarHeight()
                    val statusBarHeight = pair?.first ?: 0
                    val navBarHeight = pair?.second ?: 0
                    immersiveMode.barsHeightCallback?.invoke(statusBarHeight, navBarHeight)
                }
            }
        }
    }
}