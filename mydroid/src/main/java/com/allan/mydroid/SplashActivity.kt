package com.allan.mydroid

import android.content.Intent
import com.au.module_android.init.AbsSplashActivity

/**
 * @author allan
 * @date :2024/11/20 15:07
 * @description:
 */
class SplashActivity : AbsSplashActivity() {
    override fun goActivity(intent: Intent?) {
        startActivity(Intent(this, MyDroidActivity::class.java))
    }
}
