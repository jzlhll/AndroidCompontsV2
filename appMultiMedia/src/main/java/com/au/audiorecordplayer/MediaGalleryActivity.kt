package com.au.audiorecordplayer

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.au.audiorecordplayer.bt.BleTestFragment
import com.au.audiorecordplayer.cam1.Camera1Fragment
import com.au.audiorecordplayer.cam2.Camera2Fragment
import com.au.audiorecordplayer.camx.CameraXFragment
import com.au.audiorecordplayer.draws.DrawTestFragment
import com.au.audiorecordplayer.imgprocess.ReturnYourFaceFragment
import com.au.audiorecordplayer.particle.TransparentParticleFragment
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.FragmentShellTranslucentActivity
import com.au.module_android.widget.FlowLayout
import com.au.module_cached.delegate.AppDataStoreIntCache
import com.au.module_cached.delegate.AppDataStoreStringCache
import kotlinx.coroutines.launch

class MediaGalleryActivity : AppCompatActivity() {
    private var currentBgColorStr by AppDataStoreStringCache("media_gallery_background_index", "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_media_gallery)

        val flowLayout = findViewById<FlowLayout>(R.id.flowLayout)
        for (i in 1..300) {
            flowLayout.addView(TextView(this).also {
                it.setTextColor(Color.BLACK)
                it.text = "item $i"
            })
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initJumps()
        initChangeColors()
    }

    private fun initJumps() {
        findViewById<View>(R.id.audioRecorder).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, AudioRecorderTestFragment::class.java)
        }
        findViewById<View>(R.id.audioPlayer).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, AudioPlayerTestFragment::class.java)
        }
        findViewById<View>(R.id.bluetooth).onClick {
            BleTestFragment.start(this@MediaGalleryActivity, false)
        }
        findViewById<View>(R.id.bluetoothBle).onClick {
            BleTestFragment.start(this@MediaGalleryActivity, true)
        }
        findViewById<View>(R.id.camera1).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, Camera1Fragment::class.java)
        }
        findViewById<View>(R.id.camera1TextureView).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, Camera1Fragment::class.java)
        }
        findViewById<View>(R.id.camera2).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, Camera2Fragment::class.java)
        }
        findViewById<View>(R.id.camerax).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, CameraXFragment::class.java)
        }
        findViewById<View>(R.id.drawView).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, DrawTestFragment::class.java)
        }
        findViewById<View>(R.id.returnFaceBtn).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, ReturnYourFaceFragment::class.java)
        }
        findViewById<View>(R.id.translateParticleFull).onClick {
            Globals.mainScope.launch {
                FragmentShellTranslucentActivity.start(this@MediaGalleryActivity, TransparentParticleFragment::class.java)
            }
        }
        findViewById<View>(R.id.translateParticleActivity).onClick {
            FragmentShellActivity.start(this@MediaGalleryActivity, TransparentParticleFragment::class.java)
        }
    }

    private fun initChangeColors() {
        // 获取主ScrollView
        val mainView = findViewById<ScrollView>(R.id.main)

        if (currentBgColorStr.isNotEmpty()) {
            mainView.setBackgroundColor(Color.parseColor(currentBgColorStr))
        }

        // 红色按钮
        findViewById<View>(R.id.changeColor1).onClick {
            mainView.setBackgroundColor(Color.parseColor("#ff8800"))
            currentBgColorStr = "#ff8800"
        }

        // 绿色按钮
        findViewById<View>(R.id.changeColor2).onClick {
            mainView.setBackgroundColor(Color.parseColor("#008800"))
            currentBgColorStr = "#008800"
        }

        // 黑色按钮
        findViewById<View>(R.id.changeColor3).onClick {
            mainView.setBackgroundColor(Color.parseColor("#111111"))
            currentBgColorStr = "#111111"
        }

        // 灰色按钮
        findViewById<View>(R.id.changeColor4).onClick {
            mainView.setBackgroundColor(Color.parseColor("#eeeeee"))
            currentBgColorStr = "#fefefe"
        }

        // 蓝色按钮
        findViewById<View>(R.id.changeColor5).onClick {
            mainView.setBackgroundColor(Color.parseColor("#4460ff"))
            currentBgColorStr = "#4460ff"
        }
    }
}
