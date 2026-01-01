package com.au.jobstudy

import android.content.Intent
import com.au.module_android.init.AbsSplashActivity
import com.au.module_androidui.ui.startActivityFix

/**
 * @author allan
 * @date :2024/3/11 16:13
 * @description:
 */
class CompactSplashActivity : AbsSplashActivity() {
    override fun goActivity(intent: Intent?) {
        startActivityFix(Intent(this, MainActivity::class.java))
    }
}