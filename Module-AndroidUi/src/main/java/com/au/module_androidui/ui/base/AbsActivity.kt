package com.au.module_androidui.ui.base

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import com.au.module_android.BuildConfig
import com.au.module_android.DarkModeAndLocalesConst
import com.au.module_android.log.logdNoFile
import com.au.module_android.screenadapter.ToutiaoScreenAdapter
import com.au.module_android.utils.*
import com.au.module_androidui.ui.KEY_ENTER_ANIM
import com.au.module_androidui.ui.KEY_EXIT_ANIM
import com.au.module_androidui.ui.immersive

@Deprecated("基础框架的一环，请使用BindingActivity或者ViewActivity")
open class AbsActivity : AppCompatActivity(), IFullWindow {
    protected open val isNotCacheFragment = true //不进行自动保存Fragment用于恢复。

    /**
     * 给出额外信息的空间1
     */
    var object1:Any? = null
    /**
     * 给出额外信息的空间2
     */
    var object2:Any? = null
    /**
     * 给出额外信息的空间3
     */
    var object3:Any? = null

    //记录Activity resources configuration的uiMode
    private var mCurrentUiMode : Int = Int.MIN_VALUE

    private val enterAnim by unsafeLazy { intent.getIntExtra(KEY_ENTER_ANIM, 0) }
    private val exitAnim by unsafeLazy { intent.getIntExtra(KEY_EXIT_ANIM, 0) }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        ToutiaoScreenAdapter.attach(this)
        enableEdgeToEdgeFix()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            //todo check
            if(enterAnim != 0) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, enterAnim, 0)
            if(exitAnim != 0) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, exitAnim)
        }

        mCurrentUiMode = resources.configuration.uiMode
    }

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.PaddingBars
    }

    override fun setRequestedOrientation(requestedOrientation: Int) {
        //处理安卓8.0报错
        //Only fullscreen activities can request orientation
        ignoreError { super.setRequestedOrientation(requestedOrientation) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!isAutoHideIme() || ev == null) {
            return super.dispatchTouchEvent(ev)
        }

        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            handleAutoHideImeUp(ev)
        }
        return handled
    }

    // 在点击完成后仅根据抬手位置决定是否隐藏键盘。
    private fun handleAutoHideImeUp(event: MotionEvent) {
        val focusView = currentFocus as? EditText ?: return

        if (isShouldHideInput(focusView, event)) {
            hideImeNew(window, focusView)
        }
    }

    private fun isShouldHideInput(v: View, event: MotionEvent):Boolean {
        if (v is EditText) {
            val leftTop = intArrayOf(0, 0)
            v.getLocationInWindow(leftTop)
            val left = leftTop[0]
            val top = leftTop[1]
            val bottom = top + v.height
            val right = left + v.width
            return !(event.x > left && event.x < right && event.y > top && event.y < bottom)
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        if(isNotCacheFragment) removeCachedFragments(outState)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if(isNotCacheFragment) removeCachedFragments(outState)
    }

    private fun removeCachedFragments(outState: Bundle) {
        //清空保存Fragment的状态数据
        outState.getBundle("androidx.lifecycle.BundlableSavedStateRegistry.key")?.let {
            it.remove("android:support:fragments")
            it.remove("android:fragments")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //这里的不准。系统是先走Application的onConfigChanged，然后走这里。
        // 那么，我们会先更新themedContext，以themedContext优先。
        // 如果我们不更新application的uiMode就会有问题，而它已经deprecated。

        if (BuildConfig.SUPPORT_DARKMODE) {
            val newUiMode = DarkModeAndLocalesConst.themedContext?.resources?.configuration?.uiMode ?: newConfig.uiMode //todo
            //dark mode
            //不论是系统切换，还是app设置中强制切换都会触发Activity configurationChange
            if (DarkModeAndLocalesConst.isDarkModeFollowSystem()) {
                //1. 如果是跟随系统，有切换了，判断重建
                if (mCurrentUiMode != newUiMode) {
                    mCurrentUiMode = newUiMode
                    logdNoFile { "onConfigurationChanged system in activity newUIMode $mCurrentUiMode " }
                    recreate()
                }
            } else {
                //2. 如果不跟系统，这里得到的newConfig是不准的，可能是系统触发而来(应该抛弃)，也可能是自己设置而来（接受）
                //这个应该被抛弃，以DarkModeUtil里面为准，即AppCompatDelegate.getDefaultNightMode()
                val appIsForceDark = DarkModeAndLocalesConst.isForceDark()
                val curIsDark = (mCurrentUiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (curIsDark != appIsForceDark) {
                    logdNoFile { "onConfigurationChanged force in activity curUiMode $mCurrentUiMode curIsDark=$curIsDark, app=$appIsForceDark recreate!" }
                    recreate()
                } else {
                    logdNoFile { "onConfigurationChanged force in activity curUiMode $mCurrentUiMode curIsDark=$curIsDark, app=$appIsForceDark do nothing!" }
                }
            }
        }
    }

    open fun isAutoHideIme() = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(DarkModeAndLocalesConst.activityAttachBaseContext(newBase))
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            //todo check
            if(exitAnim != 0) overridePendingTransition(0, exitAnim)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        onWindowFocusChangedInner(hasFocus)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        onWindowFocusChangedInner(true)
    }

    /**
     * 如果你想自己控制immersive请使用它
     */
    @CallSuper
    protected open fun onWindowFocusChangedInner(hasFocus: Boolean) {
        if (hasFocus) {
            immersive(this, findViewById(android.R.id.content))
        }
    }

    ///////尺寸静态变量
    private var _bottomMargin = 0

    private val dp20 by unsafeLazy {
        (resources.getDimension(com.au.module_androidcolor.R.dimen.btn_margin_bottom) * 20f).toInt()
    }

    /**
     * 获取底部间距
     */
    fun getBottomMargin() : Int {
        if (_bottomMargin == 0) {
            val bm = resources.getDimension(com.au.module_androidcolor.R.dimen.btn_margin_bottom)
            _bottomMargin = bm.toInt()
        }
        return _bottomMargin
    }

    /**
     * 分级推荐底部间距
     */
    fun recommendBottomMargin(navBarHeight: Int): Int {
        val bm = getBottomMargin()
        return when {
            navBarHeight < dp20 -> (bm - (navBarHeight shr 1)) //底部间距减去导航栏高度的一半
            else -> bm shr 1 //底部间距减半32dp 减少到16dp
        }
    }

    /**
     * 如果我们只是想额外给某个控件追加一些间距，这个方法来推荐额外的间距值
     */
    fun recommendBottomMarginExtra(navBarHeight: Int) : Int {
        return when {
            navBarHeight > dp20 -> getBottomMargin() shr 1 //底部间距的一半
            navBarHeight < dp20 -> (navBarHeight shr 1) //导航栏高度的一半
            else -> 0 //否则不额外追加间距
        }
    }
}