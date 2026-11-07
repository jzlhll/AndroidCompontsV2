package com.au.agit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.au.agit.ui.AGitRoot
import com.au.agit.ui.AppViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** 唯一业务 Activity，仅承载 Compose 主题与导航。 */
class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var contentReady = false
        splashScreen.setKeepOnScreenCondition { !contentReady }
        setContent { AGitRoot(model, onReady = { contentReady = true }) }
    }
}
