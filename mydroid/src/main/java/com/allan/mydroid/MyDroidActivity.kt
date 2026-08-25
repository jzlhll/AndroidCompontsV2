package com.allan.mydroid

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.allan.mydroid.databinding.ActivityMyDroidBinding
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.au.module_android.utils.changeBarsColor
import com.au.module_androidui.ui.base.AbsFragment
import com.au.module_androidui.ui.base.IFullWindow
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingActivity

/** MyDroid 唯一业务 Activity，统一承载官方 Fragment Navigation。 */
class MyDroidActivity : BindingActivity<ActivityMyDroidBinding>() {
    companion object {
        const val ACTION_OPEN_SEND_SELECTOR = "com.allan.mydroid.action.OPEN_SEND_SELECTOR"
        const val EXTRA_AUTO_ENTER_SEND = "extra_auto_enter_send"

        private const val KEY_PENDING_OPEN_SEND_SELECTOR = "pending_open_send_selector"
        private const val KEY_PENDING_AUTO_ENTER_SEND = "pending_auto_enter_send"
    }

    override val isNotCacheFragment = false

    private val navHostFragment: NavHostFragment
        get() = supportFragmentManager.findFragmentById(R.id.myDroidNavHost) as NavHostFragment

    private val navController: NavController
        get() = navHostFragment.navController

    private var pendingOpenSendSelector = false
    private var pendingAutoEnterSend = false

    private val destinationChangedListener =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            updateKeepScreenOn(destination.id)
        }

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
            if (fragment == fm.primaryNavigationFragment) {
                applyCurrentPageWindowPolicy()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenSendSelector = savedInstanceState?.getBoolean(KEY_PENDING_OPEN_SEND_SELECTOR) == true
        pendingAutoEnterSend = savedInstanceState?.getBoolean(KEY_PENDING_AUTO_ENTER_SEND) == true
        captureEntryRoute(intent)

        navHostFragment.childFragmentManager.registerFragmentLifecycleCallbacks(
            fragmentLifecycleCallbacks,
            false,
        )
        navController.addOnDestinationChangedListener(destinationChangedListener)
        navController.currentDestination?.let { updateKeepScreenOn(it.id) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureEntryRoute(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        consumePendingRoute()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_PENDING_OPEN_SEND_SELECTOR, pendingOpenSendSelector)
        outState.putBoolean(KEY_PENDING_AUTO_ENTER_SEND, pendingAutoEnterSend)
        super.onSaveInstanceState(outState)
    }

    private fun captureEntryRoute(intent: Intent) {
        if (intent.action != ACTION_OPEN_SEND_SELECTOR) return

        pendingOpenSendSelector = true
        pendingAutoEnterSend = intent.getBooleanExtra(EXTRA_AUTO_ENTER_SEND, true)
        intent.action = null
        intent.removeExtra(EXTRA_AUTO_ENTER_SEND)
    }

    private fun consumePendingRoute() {
        if (!pendingOpenSendSelector) return

        pendingOpenSendSelector = false
        val autoEnter = pendingAutoEnterSend
        pendingAutoEnterSend = false

        navController.popBackStack(R.id.myDroidAllFragment, false)
        val arguments = Bundle().apply {
            putBoolean(SendListSelectorFragment.KEY_AUTO_ENTER_SEND_VIEW, autoEnter)
        }
        navController.navigate(
            R.id.sendListSelectorFragment,
            arguments,
            pageNavOptions(),
        )
    }

    private fun pageNavOptions(): NavOptions {
        return NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setEnterAnim(com.au.module_androidui.R.anim.activity_open_enter)
            .setExitAnim(com.au.module_androidui.R.anim.activity_open_exit)
            .setPopEnterAnim(com.au.module_androidui.R.anim.activity_close_enter)
            .setPopExitAnim(com.au.module_androidui.R.anim.activity_close_exit)
            .build()
    }

    private fun currentPageFragment(): Fragment? {
        return navHostFragment.childFragmentManager.primaryNavigationFragment
    }

    override fun immersiveMode(): ImmersiveMode {
        return currentPageFragment()
            ?.let { it as? IFullWindow }
            ?.immersiveMode()
            ?: ImmersiveMode.PaddingBars
    }

    override fun isAutoHideIme(): Boolean {
        return currentPageFragment()
            ?.let { it as? AbsFragment }
            ?.isAutoHideIme()
            ?: false
    }

    private fun applyCurrentPageWindowPolicy() {
        findViewById<View>(android.R.id.content).updatePadding(0, 0, 0, 0)
        changeBarsColor()
        onWindowFocusChangedInner(true)
    }

    private fun updateKeepScreenOn(destinationId: Int) {
        val keepScreenOn = when (destinationId) {
            R.id.receiveFromH5Fragment,
            R.id.sendListFilesFragment,
            R.id.textChatRoomFragment,
            R.id.connectToHostFragment -> true
            else -> false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(keepScreenOn)
        }
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        navController.removeOnDestinationChangedListener(destinationChangedListener)
        navHostFragment.childFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
        super.onDestroy()
    }
}
