package com.au.audiorecordplayer.recorder.a1mediaRecord;

import android.media.MediaRecorder

import com.au.audiorecordplayer.recorder.IRecord

import java.io.File
import java.io.IOException

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.au.module_android.Globals
import com.au.module_android.utils.ignoreError
import java.util.Calendar
import kotlin.concurrent.Volatile
import kotlin.jvm.Throws

class MediaRecordAudio : IRecord {
    private var mMediaRecorder: MediaRecorder? = null
    private var mFilePath:String? = null

    @Volatile
    private var mCurrentSt = St.NOT_INIT

    private enum class St {
        NOT_INIT,
        RECORDING,
        PAUSING,
    }

    override fun getCurrentFilePath(): String? {
        return mFilePath
    }

    override fun isRecording(): Boolean {
        return mCurrentSt == St.RECORDING
    }

    @SuppressLint("DefaultLocale")
    private fun generateACacheAudioFile() : String{
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val decisecond = calendar.get(Calendar.MILLISECOND) / 100
        val monthStr = String.format("%02d", month)
        val dayStr = String.format("%02d", day)
        val hourStr = String.format("%02d", hour)
        val minuteStr = String.format("%02d", minute)
        val secondStr = String.format("%02d", second)
        val datePart = "${year}_${monthStr}_${dayStr}_${hourStr}${minuteStr}${secondStr}_$decisecond"
        // 录音使用 AAC（MPEG-4 容器），与业务侧 MP3/WAV/AAC/OGG 中的 AAC 一致；后缀 .m4a
        val filePath = "${Globals.goodCacheDir.absolutePath}/audio_$datePart.m4a"
        return filePath
    }

    @Throws(IOException::class)
    override fun start(context: Context) {
        if (mCurrentSt != St.NOT_INIT) {
            throw RuntimeException("")
        }

        val filePath = generateACacheAudioFile()

        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }

        mMediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        mMediaRecorder?.let { mr->
                mr.setAudioSource(MediaRecorder.AudioSource.MIC) // 音频输入源
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // AAC 容器
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // AAC 编码

            mr.setAudioSamplingRate(44100)
            mr.setAudioEncodingBitRate(128000)

            mr.setOutputFile(filePath) //设置输出文件的路径

            mFilePath = filePath

            mr.prepare() //准备录制
            mr.start() //开始录制
        }

        mCurrentSt = St.RECORDING
    }

    override fun stop() {
        val mr = mMediaRecorder ?: return

                ignoreError {
            if (mCurrentSt == St.RECORDING || mCurrentSt == St.PAUSING) {
                mr.stop()
            }
        }
        if (mMediaRecorder != null) mr.release()
        mMediaRecorder = null
        mCurrentSt = St.NOT_INIT
    }

    override fun resume() {
        val mr = mMediaRecorder ?: return

        if (mCurrentSt == St.PAUSING) {
            mr.resume()
            mCurrentSt = St.RECORDING
        }
    }

    override fun pause() {
        val mr = mMediaRecorder ?: return

        if (mCurrentSt == St.RECORDING) {
            mr.pause()
            mCurrentSt = St.PAUSING
        }
    }

    fun getMaxAmplitude(): Int {
        if (mCurrentSt != St.RECORDING) return 0
        return mMediaRecorder?.maxAmplitude ?: 0
    }
}

