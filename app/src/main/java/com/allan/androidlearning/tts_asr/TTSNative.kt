package com.allan.androidlearning.tts_asr

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

// 在 MainActivity 类内部或外部定义这个接口类：
class TTSNative(private val context: Context) : ITts{
    private var tts: TextToSpeech? = null
    private var mDoneCb: () -> Unit = {}

    override fun setOnDoneCallback(cb: () -> Unit) {
        mDoneCb = cb
    }

    override fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // TTS 引擎初始化成功，可以设置语言等
                // 注意：设置语言的结果需要检查，可能不支持某种语言
                tts?.language = Locale.ENGLISH
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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

    override fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts?.setOnUtteranceProgressListener(null)
    }

    override fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun stop() {
        tts?.stop()
    }
}