/*
* Created by allan on 2026/03/27.
*
* Copyright (C) 2026 [allan]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@allan]
*/

package com.au.audiorecordplayer.player

interface IPlayer {
    /**
     * 打开文件并 prepare，不 start；用于预览页等用户手动点播放。
     */
    fun prepareWithoutStart(filePath: String)

    fun play(filePath: String)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Int)
    val isPlaying: Boolean
    val currentPosition: Int
    val duration: Int
    fun setOnCompletionListener(listener: () -> Unit)
    fun release()
}
