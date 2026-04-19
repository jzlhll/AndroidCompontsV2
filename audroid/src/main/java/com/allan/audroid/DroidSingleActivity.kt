package com.allan.audroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.allan.audroid.navigation.AppNavHost
import com.allan.audroid.theme.AndroidCompontsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_STAY_MILLIS = 2000L

class DroidSingleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        var keepSplashOnScreen = true
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(SPLASH_STAY_MILLIS)
            keepSplashOnScreen = false
        }

        setContent {
            AndroidCompontsTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
