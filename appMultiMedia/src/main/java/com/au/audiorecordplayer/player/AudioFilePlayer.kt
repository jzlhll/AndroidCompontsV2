/*
* Created by jiangzhonglun@imagecho.ai on 2026/03/27.
*
* Copyright (C) 2026 [imagecho.ai]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@imagecho.ai]
*/

package com.au.audiorecordplayer.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge

class AudioFilePlayer : IPlayer {

    private var mMediaPlayer: MediaPlayer? = null
    private var mOnCompletion: (() -> Unit)? = null

    override val isPlaying: Boolean
        get() = mMediaPlayer?.isPlaying == true

    override val currentPosition: Int
        get() = mMediaPlayer?.currentPosition ?: 0

    override val duration: Int
        get() = mMediaPlayer?.duration ?: 0

    override fun prepareWithoutStart(filePath: String) {
        logdNoFile { "prepareWithoutStart filePath=$filePath" }
        mMediaPlayer?.release()
        mMediaPlayer = null
        try {
            mMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(filePath)
                setOnCompletionListener { mOnCompletion?.invoke() }
                prepare()
            }
        } catch (e: Exception) {
            loge { "prepareWithoutStart failed: ${e.message}" }
            mMediaPlayer?.release()
            mMediaPlayer = null
        }
    }

    override fun play(filePath: String) {
        logdNoFile { "play filePath=$filePath" }
        mMediaPlayer?.release()
        mMediaPlayer = null
        try {
            mMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(filePath)
                setOnCompletionListener { mOnCompletion?.invoke() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            loge { "play failed: ${e.message}" }
            mMediaPlayer?.release()
            mMediaPlayer = null
        }
    }

    override fun pause() {
        logdNoFile { "pause" }
        mMediaPlayer?.let {
            if (it.isPlaying) it.pause()
        }
    }

    override fun resume() {
        logdNoFile { "resume" }
        mMediaPlayer?.let {
            if (!it.isPlaying) it.start()
        }
    }

    override fun stop() {
        logdNoFile { "stop" }
        mMediaPlayer?.let {
            if (it.isPlaying) it.stop()
        }
    }

    override fun seekTo(positionMs: Int) {
        mMediaPlayer?.seekTo(positionMs)
    }

    override fun setOnCompletionListener(listener: () -> Unit) {
        mOnCompletion = listener
    }

    override fun release() {
        logdNoFile { "release" }
        mMediaPlayer?.release()
        mMediaPlayer = null
        mOnCompletion = null
    }
}
