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
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_MATCH_ACTIVITY_CLOSE
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_MATCH_ACTIVITY_OPEN
import androidx.lifecycle.ViewModelProvider
import com.au.module_android.BuildConfig
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.unsafeLazy
import com.au.module_androidui.R
import com.au.module_androidui.ui.FragmentShellActivity.Companion.KEY_ENTER_ANIM
import com.au.module_androidui.ui.FragmentShellActivity.Companion.KEY_EXIT_ANIM
import com.au.module_androidui.ui.base.AbsFragment
import com.au.module_androidui.ui.base.IFullWindow
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.navigation.FragmentNavigationConfig
import com.au.module_androidui.ui.navigation.FragmentNavigationScene
import com.au.module_androidui.ui.navigation.FragmentNavigationViewModel
import com.au.module_androidui.ui.views.ViewActivity
import com.au.module_simplepermission.activity.ActivityForResult

open class FragmentNavigationActivity : ViewActivity() {
    companion object {
        fun start(context: Context,
                  sceneId: String,
                  activityResult: ActivityForResult? = null,
                  optionsCompat: ActivityOptionsCompat? = null,
                  enterAnim:Int? = null,
                  exitAnim:Int? = null,
                  activityResultCallback:ActivityResultCallback<ActivityResult>? = null) {
            val intent = Intent(context, FragmentNavigationActivity::class.java)

            FragmentNavigationScene.putIntent(intent, FragmentNavigationConfig.getScene(sceneId))
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

    private val mEnterAnim by unsafeLazy { intent.getIntExtra(KEY_ENTER_ANIM, 0) }
    private val mExitAnim by unsafeLazy { intent.getIntExtra(KEY_EXIT_ANIM, 0) }

    lateinit var fragmentNavigationScene : FragmentNavigationScene

    override val exitAnim: Int?
        get() = mExitAnim

    override val enterAnim: Int?
        get() = mEnterAnim

    val viewModel by unsafeLazy { ViewModelProvider(this)[FragmentNavigationViewModel::class.java] }

    private var mBackstackPageIds = mutableListOf<String>()

    private lateinit var fcv : FragmentContainerView

    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = FragmentContainerView(inflater.context)
        this.fcv = v
        v.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        v.id = View.generateViewId()

        val scene = FragmentNavigationScene.fromIntent(intent)
        viewModel.initScene(scene)

        val entryParams = scene.entryParams
        val pageId = FragmentNavigationConfig.checkEntryParams(scene.sceneId, entryParams) ?: scene.startPageId
        navigateTo(pageId)

        return v
    }

    fun navigateTo(pageId: String,
                   clearCurrent: Boolean = false,
                   transitionId:Int = TRANSIT_FRAGMENT_MATCH_ACTIVITY_OPEN) {
        navigateToInner(pageId, clearCurrent, transitionId, false)
    }

    private fun navigateToInner(pageId: String,
                                clearCurrent: Boolean = false,
                                transitionId:Int= TRANSIT_FRAGMENT_MATCH_ACTIVITY_OPEN,
                                isBack: Boolean) {
        val fragmentClass = viewModel.scene.list.first { it.pageId == pageId }.fragmentClass

        val instance = fragmentClass.getDeclaredConstructor().newInstance()
        //添加传统传入的机制，兼容旧版本。你可以继续使用 arguments 获取每次 navigate 进来后的参数
        instance.arguments = viewModel.getPageData(pageId)

        mIsAutoHideIme = instance.asOrNull<AbsFragment>()?.isAutoHideIme() ?: false

        if (BuildConfig.DEBUG) {
            Log.d("AU_APP", "FragmentShellActivity: ${fragmentClass.name}")
        }

        if (clearCurrent || isBack) {
            mBackstackPageIds.removeLastOrNull()
        }
        if (!isBack) {
            mBackstackPageIds.add(pageId)
        }

        //1️⃣。
        // 作为容器，我们将immersiveMode()返回FullImmersive，得到的结果就是activity完全沉浸。
        //至于padding交给Fragment处理。
        if (instance is IFullWindow) {
            instance.postPaddingRootInner(this, fcv)
        }

        supportFragmentManager.beginTransaction().also {
            it.setTransition(transitionId)
            it.replace(fcv.id, instance)
            it.commit()
        }
    }

    /**
     * 返回 false，就表示不要做 finish；返回 true 就要 finish。
     * @param clearTo 如果为空，则向上一层；不为空，则表示从当前页面开始，向上清除到 clearTo 的页面。
     * @param extraParams 如果不为空，则表示在返回时，将 extraParams 传给前一个页面。
     */
    fun navigateBack(clearTo:String? = null, extraParams:Map<String, Any?>? = null) {
        if (mBackstackPageIds.size <= 1) {
            finishAfterTransition()
            return
        }

        var backPageId :String? = null
        if (clearTo == null) {
            mBackstackPageIds.removeLastOrNull()
            val last = mBackstackPageIds.last()
            backPageId = last
        } else {
            while (mBackstackPageIds.size > 1) {
                val last = mBackstackPageIds.removeLastOrNull()
                if (last == clearTo) {
                    backPageId = last
                    break
                }
            }
        }

        if (backPageId == null) {
            throw RuntimeException("clearTo: $clearTo not found")
        }

        if(extraParams != null) viewModel.updatePageData(backPageId, extraParams) //将数据写到前一个页面上。
        navigateToInner(backPageId, transitionId = TRANSIT_FRAGMENT_MATCH_ACTIVITY_CLOSE, isBack = true)
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
}