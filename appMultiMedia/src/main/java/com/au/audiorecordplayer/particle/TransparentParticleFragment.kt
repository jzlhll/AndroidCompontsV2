package com.au.audiorecordplayer.particle

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import com.au.audiorecordplayer.databinding.FragmentFloatParticleBinding
import com.au.audiorecordplayer.recorder.ISimpleRecord
import com.au.audiorecordplayer.recorder.a2AudioRecord.WavePcmAudioRecord
import com.au.audiorecordplayer.util.MainUIManager
import com.au.module_android.click.onClick
import com.au.module_android.permissions.createPermissionForResult
import com.au.module_android.ui.bindings.BindingFragment
import com.au.module_android.utils.ALogJ

class TransparentParticleFragment : BindingFragment<FragmentFloatParticleBinding>() {
    val permissionHelper = createPermissionForResult(android.Manifest.permission.RECORD_AUDIO)

    var mRecord: ISimpleRecord? = null

    private fun startRecord() {
        permissionHelper.safeRun({
            runCatching {
                mRecord?.start()
               // mScreenEffectView?.onVoiceStarted()
            }.onFailure {
                MainUIManager.get().toastSnackbar(binding.btn, "开始失败-" + it.message)
            }
        }, notGivePermissionBlock = {
            MainUIManager.get().toastSnackbar(binding.btn, "没有授予权限")
        })
    }

    private fun initAndStartRecord() {
        WavePcmAudioRecord().also {
            mRecord = it
            it.setWaveDetectCallback { rms, db->
                ALogJ.t("wave rms: $rms db: $db")
              //  mScreenEffectView?.onRmsUpdated(rms)
            }
            startRecord()
        }
    }

    private fun stopRecord() {
        mRecord?.stop()
        mRecord = null
       // mScreenEffectView?.onVoiceStopped()
        MainUIManager.get().toastSnackbar(binding.btn, "录制已经停止")
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)
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
                addView(WaveParabolaView(context).also {
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

    override fun isPaddingStatusBar(): Boolean {
        return false
    }

    override fun isPaddingNavBar(): Boolean {
        return false
    }
}