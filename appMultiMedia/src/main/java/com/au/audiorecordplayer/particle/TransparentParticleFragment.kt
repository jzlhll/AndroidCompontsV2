package com.au.audiorecordplayer.particle

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.au.audiorecordplayer.databinding.FragmentFloatParticleBinding
import com.au.audiorecordplayer.recorder.ISimpleRecord
import com.au.audiorecordplayer.recorder.IWaveDetectRecord
import com.au.audiorecordplayer.recorder.a2AudioRecord.WavePcmAudioRecord
import com.au.audiorecordplayer.util.MainUIManager
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_simplepermission.createPermissionForResult

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TransparentParticleFragment : BindingFragment<FragmentFloatParticleBinding>() {
    val permissionHelper = createPermissionForResult(Manifest.permission.RECORD_AUDIO)

    private var mWave : ScreenEffectParticleWaveView? = null

    var mRecord: ISimpleRecord? = null

    private fun startRecord() {
        permissionHelper.safeRun( notGivePermissionBlock = {
            MainUIManager.get().toastSnackbar(binding.btn, "没有授予权限")
        }){
            runCatching {
                mRecord?.start()
                mWave?.setVoiceIsRecording(true)
            }.onFailure {
                MainUIManager.get().toastSnackbar(binding.btn, "开始失败-" + it.message)
            }
        }
    }

    private fun initAndStartRecord() {
        WavePcmAudioRecord().also {
            mRecord = it
            it.setWaveDetectCallback(object : IWaveDetectRecord.IWaveDetectCallback {
                override fun onWaveDetect(db: Double) {
                    mWave?.onVoiceDbUpdated(db)
                }
            })
            startRecord()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mRecord?.stop()
        mRecord = null
    }

    private fun stopRecord() {
        mRecord?.stop()
        mRecord = null
        mWave?.setVoiceIsRecording(false)
        MainUIManager.get().toastSnackbar(binding.btn, "录制已经停止")
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.btn.onClick {
            if (mRecord != null) {
                binding.btn.text = "开始录音"
                stopRecord()
            } else {
                binding.btn.text = "停止"
                initAndStartRecord()
            }
        }

        binding.container.apply {
            //如果大于等于13才显示
            if (true) {
                addView(ScreenEffectParticleWaveView(context).also {
                    mWave = it
                    it.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
            } else {
                addView(ScreenEffectLowView(context).also {
                    it.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                })
            }
        }
    }

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive
    }
}