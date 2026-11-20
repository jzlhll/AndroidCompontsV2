package com.allan.androidlearning.tts_asr

import android.Manifest
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.allan.androidlearning.databinding.FragmentAsrAndTtsBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.visible
import com.au.module_androidui.toast.MyToastUtil
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_simplepermission.createPermissionForResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel

/**
 * android 搭建本地实现语音识别功能 vosk
https://www.cnblogs.com/yy2000/p/18819924
模型：
https://alphacephei.com/vosk/models

English
vosk-model-small-en-us-0.15	     40M	  9.85
vosk-model-en-us-0.22	         1.8G	  5.69
vosk-model-en-us-0.22-lgraph	 128M	  7.82
vosk-model-en-us-0.42-gigaspeech 2.3G	  5.64

SherpaOnnx TTS ASR都有

Rhasspy 方案
https://blog.csdn.net/gitblog_00364/article/details/148862557

🎤 ASR

Vosk	离线运行5、多语言支持5、轻量级（最小模型仅50MB左右）5	完全免费开源 (Apache 2.0)5	嵌入式设备、隐私敏感场景、离线环境5
FunASR	由阿里巴巴开源9、支持高并发9、可本地部署9	完全免费开源9	智能客服、会议转录、需要大规模处理的场景9
NEMO ASR API	基于英伟达NEMO1、提供预训练模型1	提供Demo和API接口1，部分预训练模型可下载试用1	开发者测试、体验英伟达ASR技术
百度语音平台	端到端语音语言大模型3、提供Android SDK3	部分功能邀测3	移动端应用集成


🔊 TTS
https://audiotexthub.pro/auth/register

OpenAI-Edge-TTS	本地部署模拟OpenAI TTS端点2、使用Microsoft Edge TTS服务2、支持多种语音和音频格式2	完全免费2	需要与OpenAI TTS API兼容的免费本地替代方案2
Google gTTS	云端服务10、无需本地配置10	完全免费10	快速原型开发、资源受限环境10
ZTSonos-T	支持实时语音克隆和多语言情感控制6、基于Apache2.0许可证开源6	每月免费生成100分钟音频6	需要高质量、多情感语音合成的项目6
微软TTS Azure	高自然度、多语言多音色支持8、企业级SLA保障8、国内由世纪互联运营（合规性较好）4	提供免费试用额度4	企业级应用、对稳定性和合规性要求高的场景4
腾讯云TTS	离在线混合合成模式7

 离线模型：
eSpeak NG	轻量级、低资源占用、无需网络、开箱即用14	机械音明显	多语言（支持中文）	⭐⭐	GPLv3
Piper	基于神经网络、语音自然度较高5	较高	多语言	⭐⭐⭐⭐	MIT
Mimic3	Mycroft 项目开发、平衡性能与音质、支持多语言韵律优化5	较高	多语言	⭐⭐⭐	Apache 2.0
SherpaOnnx	使用 next-gen Kaldi 和 onnxruntime，无需互联网连接，支持嵌入式系统和移动平台2	较高	多语言	⭐⭐⭐	未明确
MaryTTS	开源、支持多种语言和语音，提供高级功能8	中等	多语言	⭐⭐⭐	未明确
MultiTTS	聚合多种音源、无需联网、音色丰富（需导入音源包）3710	较高（依赖音源）	多语言	⭐⭐	未明确
Flite	非常轻量级、适合资源极度受限的场景	基础	主要英文	⭐⭐	BSD
Kitten TTS
 */

@EntryFrgName()
class AsrAndTtsFragment : BindingFragment<FragmentAsrAndTtsBinding>() {
    override fun isAutoHideIme() = true

    companion object {
        const val TAG = "au_asr"
        private const val STOP_TEXT = "stop native Vosk ASR"
    }

    private val permissionHelper = createPermissionForResult(Manifest.permission.RECORD_AUDIO)
    private lateinit var mTts: Array<ITts>

    private val viewModel by unsafeLazy { ViewModelProvider(requireActivity())[AsrAndTtsViewModel::class.java] }

    private val currentASRText = StringBuilder("")

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        mTts = arrayOf(
            TTSNative(requireContext()),
            TTSEdgeOnline()
        )
        for (tts in mTts) {
            tts.init()
            tts.setOnDoneCallback {
                lifecycleScope.launch {
                    setTtsButtonStatus(true)
                }
            }
        }
        permissionHelper.safeRun(notGivePermissionBlock = {
            MyToastUtil.toastOnTopError("录音权限，授权未通过！")
        }){
            MyToastUtil.toastOnBottom("录音权限，授权通过！")
        }

        initTts()
        initAsr()
    }

    private fun initAsr() {
        binding.asrButton.onClick {
            changeAsrButtons(true)
            viewModel.dispatch(AsrAndTtsViewModel.StartRecognizeAction())
        }
        binding.stopAsrBtn.onClick {
            changeAsrButtons(false)
            viewModel.dispatch(AsrAndTtsViewModel.StopRecognizeAction())
        }

        // 初始化 Vosk
        LibVosk.setLogLevel(LogLevel.INFO)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentAsrFlow.collectStatusState(
                    onLoading = {
                        binding.loading.visible()
                    },
                    onSuccess = { bean->
                        when (bean) {
                            is AsrAndTtsViewModel.UiBean -> {
                                currentASRText.append(bean.asr).append(" ")
                                binding.asrText.setText(currentASRText.toString())
                            }
                            else -> {
                                binding.loading.gone()
                                binding.asrText.setText("")
                                currentASRText.clear()
                            }
                        }
                    },
                    onError = {
                    }
                )
            }
        }

        viewModel.dispatch(AsrAndTtsViewModel.UnzipAsrModelAction())
    }

    private fun setTtsButtonStatus(isEnable: Boolean) {
        binding.nativeTtsButton.isEnabled = isEnable
        binding.edgeTtsButton.isEnabled = isEnable
    }

    private fun initTts() {
        binding.nativeTtsButton.onClick {
            callTts(0)
        }
        binding.edgeTtsButton.onClick {
            callTts(1)
        }
        binding.nativeEdit.doAfterTextChanged {
            if (!it.isNullOrEmpty()) {
               setTtsButtonStatus(true)
            } else {
                setTtsButtonStatus(false)
            }
        }
    }

    private fun callTts(index:Int) {
        val tts = binding.nativeEdit.text?.toString()
        if (!tts.isNullOrEmpty()) {
            setTtsButtonStatus(false)
            lifecycleScope.launchOnIOThread {
                mTts.getOrNull(index)?.speak(tts)
            }
        }
    }

    private var mStopTextJob: Job? =null
    private var count = 0
    private fun dotCount() : String{
        if (count % 3 == 0) {
            return "."
        }
        if (count % 3 == 1) {
            return ".."
        }
        return "..."
    }


    private fun changeAsrButtons(recoding : Boolean) {
        mStopTextJob?.cancel()

        if (recoding) {
            binding.stopAsrBtn.visible()
            binding.asrButton.gone()
            mStopTextJob = lifecycleScope.launch {
                while (true) {
                    delay(500)
                    val text = STOP_TEXT + dotCount()
                    count++
                    binding.stopAsrBtn.text = text
                }
            }
        } else {
            binding.stopAsrBtn.gone()
            binding.asrButton.visible()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mTts.forEach {
            it.destroy()
        }
        mStopTextJob?.cancel()
        viewModel.dispatch(AsrAndTtsViewModel.StopRecognizeAction())
    }
}
