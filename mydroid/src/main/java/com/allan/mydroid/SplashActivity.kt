package com.allan.mydroid

import android.content.Intent
import android.os.Bundle
import com.allan.mydroid.views.MyDroidAllFragment
import com.allan.mydroid.views.send.SendListSelectorFragment.Companion.KEY_START_TYPE
import com.au.module_android.init.AbsSplashActivity
import com.au.module_androidui.ui.FragmentShellActivity

/**
 * @author allan
 * @date :2024/11/20 15:07
 * @description:
 */
class SplashActivity : AbsSplashActivity() {
    override fun goActivity(intent: Intent?) {
        val startTypeValue = intent?.getStringExtra(KEY_START_TYPE)
        if (startTypeValue == null) {
            FragmentShellActivity.start(this, MyDroidAllFragment::class.java)
        } else {
            intent.removeExtra(KEY_START_TYPE)
            FragmentShellActivity.start(this, MyDroidAllFragment::class.java,
                Bundle().apply { putString(KEY_START_TYPE, startTypeValue) }
            )
        }
    }
}