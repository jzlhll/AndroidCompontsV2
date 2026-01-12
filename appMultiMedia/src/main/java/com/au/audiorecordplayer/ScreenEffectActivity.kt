package com.au.audiorecordplayer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.au.module_android.utils.enableEdgeToEdgeFix

class ScreenEffectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeFix()
        setContentView(R.layout.activity_screen_effect)
    }
}
