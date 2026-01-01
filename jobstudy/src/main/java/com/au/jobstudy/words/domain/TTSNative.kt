package com.au.jobstudy.words.domain

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.tryGetContext
import java.util.Locale
import java.util.UUID

// 在 MainActivity 类内部或外部定义这个接口类：
class TTSNative() : DefaultLifecycleObserver {
    private var tts: TextToSpeech? = null
    private var mDoneCb: () -> Unit = {}

    private val logTag = UUID.randomUUID().toString().substring(0, 8)

    fun showLog() {
        logdNoFile { "${this@TTSNative}: $logTag" }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (tts == null) {
            val context = owner.tryGetContext()!!
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let {
                        // TTS 引擎初始化成功，可以设置语言等
                        // 注意：设置语言的结果需要检查，可能不支持某种语言
                        it.language = Locale.SIMPLIFIED_CHINESE
                        it.setSpeechRate(0.65f)
                    }
                }
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    mDoneCb()
                }

                override fun onError(utteranceId: String?) {

                }

                override fun onStart(utteranceId: String?) {
                }

            })
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        tts?.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        tts?.stop()
        tts?.shutdown()
        tts?.setOnUtteranceProgressListener(null)
    }

    fun setOnDoneCallback(cb: () -> Unit) {
        mDoneCb = cb
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
}