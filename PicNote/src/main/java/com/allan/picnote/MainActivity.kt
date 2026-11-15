package com.allan.picnote

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.allan.picnote.ui.PicNoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleLog("on_create", " restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent { PicNoteApp(application as PicNoteApplication) }
    }

    override fun onStart() { super.onStart(); lifecycleLog("on_start") }
    override fun onResume() { super.onResume(); lifecycleLog("on_resume") }
    override fun onPause() { lifecycleLog("on_pause"); super.onPause() }
    override fun onStop() { lifecycleLog("on_stop"); super.onStop() }
    override fun onDestroy() { lifecycleLog("on_destroy", " changingConfigurations=$isChangingConfigurations"); super.onDestroy() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        lifecycleLog("window_focus", " hasFocus=$hasFocus")
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        lifecycleLog("top_resumed", " topResumed=$isTopResumedActivity")
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        lifecycleLog("multi_window", " multiWindow=$isInMultiWindowMode")
    }

    private fun lifecycleLog(event: String, details: String = "") {
        val app = application as PicNoteApplication
        logd("event=activity_$event startupId=${app.startupId} pid=${app.processId} foregroundOrdinal=${app.foregroundOrdinal} activityId=${System.identityHashCode(this)} multiWindow=$isInMultiWindowMode windowFocus=${hasWindowFocus()}$details")
    }
}
