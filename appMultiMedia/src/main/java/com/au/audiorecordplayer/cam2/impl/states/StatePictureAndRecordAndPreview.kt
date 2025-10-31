package com.au.audiorecordplayer.cam2.impl.states

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.view.Surface
import com.au.audiorecordplayer.cam2.base.IActionRecord
import com.au.audiorecordplayer.cam2.impl.DataRepository
import com.au.audiorecordplayer.cam2.impl.IStateTakePictureRecordCallback
import com.au.audiorecordplayer.cam2.impl.MyCamManager
import com.au.audiorecordplayer.cam2.impl.NeedSizeUtil
import com.au.audiorecordplayer.util.FileUtil
import com.au.audiorecordplayer.util.MyLog
import com.au.module_android.Globals
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.logdNoFile

class StatePictureAndRecordAndPreview(mgr: MyCamManager) : StatePictureAndPreview(mgr), MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener, IActionRecord {
    private var mMediaRecorder: MediaRecorder? = null

    private var mLastMp4: String? = null

    override fun allIncludePictureSurfaces(): List<Surface> {
        if (mMediaRecorder == null) {
            logdNoFile { "all include pic surfaces media record is null" }
            return super.allIncludePictureSurfaces()
        }
        logdNoFile { "all include pic surfaces $mMediaRecorder" }
        return listOf(mTakePic!!.surface, DataRepository.surface!!, mMediaRecorder?.surface!!)
    }

    init {
        logdNoFile { "init" }
        try {
            mMediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(Globals.topActivity.asOrNull<Activity>() ?: Globals.app) //later: 这里这样传递context其实不太好。
            } else {
                MediaRecorder()
            }

            mMediaRecorder?.also {
                it.setOnErrorListener(this)
                it.setOnInfoListener(this)
                it.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                it.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                it.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

                var wishWidth = 1920
                var wishHeight = 1080
                val cameraId = DataRepository.cameraId
                if (cameraId == CameraCharacteristics.LENS_FACING_FRONT) {
                    wishHeight = 1280
                    wishWidth = 720
                }
                val systemCameraManager = Globals.app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val needSize = NeedSizeUtil
                    .getByClz(MediaRecorder::class.java, systemCameraManager, "" + cameraId, wishWidth, wishHeight)
                    .needSize("StatePictureAndRecordAndPreview")
                it.setVideoSize(needSize.width, needSize.height)
                it.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                val camPro = CamcorderProfile.get(
                    cameraId,
                    if (cameraId == CameraCharacteristics.LENS_FACING_FRONT) CamcorderProfile.QUALITY_1080P else CamcorderProfile.QUALITY_720P
                )
                it.setAudioEncoder(camPro.audioCodec)
                it.setAudioChannels(camPro.audioChannels)
                // mMediaRecorder.setAudioSamplingRate(camPro.audioSampleRate);
                it.setAudioSamplingRate(16000)
                it.setAudioEncodingBitRate(camPro.audioBitRate)
                it.setVideoEncodingBitRate(camPro.videoBitRate / 2) //码率，自行调节，我希望录制小一点码率
                it.setVideoFrameRate(camPro.videoFrameRate)
                MyLog.d("Video frame " + camPro.videoFrameRate + " bitRate " + camPro.videoBitRate / 2)
                // mMediaRecorder.setMaxDuration(video.duration);
                // mMediaRecorder.setMaxDuration(30000/*video.duration*/);
                val lastMp4 = FileUtil.getNextRecordFilePath(".mp4")
                mLastMp4 = lastMp4
                it.setOutputFile(lastMp4)
                it.prepare()
            }
        } catch (e: Exception) {
            MyLog.ex(e)
            logdNoFile { "media record is null err" }
            mMediaRecorder?.release()
            mMediaRecorder = null
        }
    }

    override fun createCaptureBuilder(cameraDevice: CameraDevice): CaptureRequest.Builder {
        logdNoFile{"000"}
        val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        logdNoFile{"111"}
        captureRequestBuilder.addTarget(DataRepository.surface!!)
        logdNoFile{"222 " + mMediaRecorder}
        captureRequestBuilder.addTarget(mMediaRecorder!!.surface)
        logdNoFile{"333"}
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        logdNoFile{"444"}
        return captureRequestBuilder
    }

    override fun s1_createCaptureSessionStateCallback(cameraDevice: CameraDevice): CameraCaptureSession.StateCallback {
        return object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                camSession = cameraCaptureSession
                s2_camCaptureSessionSetRepeatingRequest(cameraDevice, cameraCaptureSession)
                try {
                    if (mMediaRecorder == null) {
                        MyLog.e("error!!!! mediaRecord is null")
                    }
                    mMediaRecorder?.start()
                    mStateBaseCb.asOrNull<IStateTakePictureRecordCallback>()?.onRecordStart(true)
                } catch (ignored: CameraAccessException) {
                }
            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                mStateBaseCb?.asOrNull<IStateTakePictureRecordCallback>()?.onRecordStart(false)
            }
        }
    }

    @Synchronized
    override fun stopRecord() {
        val mediaRecorder = mMediaRecorder
        if (mediaRecorder == null) return
        mediaRecorder.setOnErrorListener(null)
        mediaRecorder.setOnInfoListener(null)
        ignoreError {
            mediaRecorder.stop()
            mediaRecorder.release()
        }
        mMediaRecorder = null
        mStateBaseCb.asOrNull<IStateTakePictureRecordCallback>()?.onRecordEnd(mLastMp4)
    }

    override fun closeSession() {
        stopRecord()
        super.closeSession()
    }

    override fun onError(mediaRecorder: MediaRecorder?, i: Int, i1: Int) {
        mStateBaseCb.asOrNull<IStateTakePictureRecordCallback>()?.onRecordError(i)
    }

    override fun onInfo(mediaRecorder: MediaRecorder?, i: Int, i1: Int) {
        //TODO file reach file finish
    }
}

