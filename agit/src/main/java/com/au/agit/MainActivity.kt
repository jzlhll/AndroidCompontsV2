package com.au.agit

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.au.agit.model.OperationStatus
import com.au.agit.ui.AGitRoot
import com.au.agit.ui.AppViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/** 唯一业务 Activity，承载 Compose 主题与导航，并按 Clone 状态管理屏幕常亮。 */
class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        var contentReady = false
        splashScreen.setKeepOnScreenCondition { !contentReady }
        setContent { AGitRoot(model, onReady = { contentReady = true }) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    model.operationFlow.map { operation ->
                        operation?.kind == "clone" && operation.status == OperationStatus.Running
                    }.distinctUntilChanged().collect { keepScreenOn ->
                        if (keepScreenOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                } finally {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}
