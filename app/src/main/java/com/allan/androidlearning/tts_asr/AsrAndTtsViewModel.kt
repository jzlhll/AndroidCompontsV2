package com.allan.androidlearning.tts_asr

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allan.androidlearning.tts_asr.AsrAndTtsFragment.Companion.TAG
import com.au.module_android.Globals
import com.au.module_android.simpleflow.ActionDispatcherImpl
import com.au.module_android.simpleflow.IActionDispatcher
import com.au.module_android.simpleflow.IStateAction
import com.au.module_android.simpleflow.StatusState
import com.au.module_androidui.toast.ToastUtil.toastOnTop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class AsrAndTtsViewModel : ViewModel(), IActionDispatcher by ActionDispatcherImpl() {
    private var voskSpeechService: SpeechService? = null

    private var voskModel: Model? = null

    class ClearAsrStringAction : IStateAction
    class UnzipAsrModelAction : IStateAction
    class StartRecognizeAction : IStateAction
    class StopRecognizeAction : IStateAction

    open class InitUiBean
    class UiBean(val asr:String) : InitUiBean()

    private val _currentAsrFlow = MutableStateFlow<StatusState<InitUiBean>>(StatusState.Loading)
    val currentAsrFlow: StateFlow<StatusState<InitUiBean>> = _currentAsrFlow.asStateFlow()

    init {
        getActionStore().apply {
            reduce(UnzipAsrModelAction::class.java) {
                unpackVosk()
            }
            reduce(StartRecognizeAction::class.java) {
                startListening()
            }
            reduce(StopRecognizeAction::class.java) {
                stopListening()
            }
            reduce(ClearAsrStringAction::class.java) {
                viewModelScope.launch {
                    _currentAsrFlow.value = StatusState.Success(InitUiBean())
                }
            }
        }
    }

    private fun unpackVosk() {
        StorageService.unpack(
            Globals.app,
            "vosk_en_lgraph_022",
            "voice_model",
            { model->
                this.voskModel = model
                _currentAsrFlow.value = StatusState.Success(InitUiBean())
            },
            {
                toastOnTop("解压vosk ASR模型失败。")
            }
        )
    }

    private val voskListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
//            if(hypothesis.isNullOrEmpty())return
//            try {
//                if (true) {
//                    if (hypothesis.contains("""
//"partial" : ""
//                    """.trimIndent())) {
//                        return
//                    }
//                }
//                Log.d(TAG, "on PartialResult=$hypothesis")
//                val jsonObject = JSONObject(hypothesis)
//                val partial = jsonObject.getString("partial")
//                Log.d(TAG, "on Partial=$partial")
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
        }

        override fun onResult(hypothesis: String?) {
            if(hypothesis.isNullOrEmpty())return
            Log.d(TAG, "on Result=$hypothesis")
            try {
                val jsonObject = JSONObject(hypothesis)
                val text = jsonObject.getString("text")
                Log.d(TAG, "on Result2=$text")

                _currentAsrFlow.value = StatusState.Success(UiBean(text))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            if(hypothesis.isNullOrEmpty())return
            Log.d(TAG, "on FinalResult=$hypothesis")
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Recognition error", exception)
        }

        override fun onTimeout() {
            onTimeoutFun()
        }
    }

    private fun onTimeoutFun() {
        voskSpeechService?.let {
            it.stop()
            it.startListening(voskListener)
        }
    }

    private fun startListening() {
        try {
            val voskService = voskSpeechService
            if (voskService == null) {
                val recognizer = Recognizer(voskModel, 16000.0f)
                voskSpeechService = SpeechService(recognizer, 16000.0f).also {
                    it.startListening(voskListener)
                }
            } else {
                voskService.startListening(voskListener)
            }
        } catch (e: IOException) {
            Log.e("Vosk", "Failed to start speech service", e)
        }
    }

    private fun stopListening() {
        voskSpeechService?.stop()
    }
}