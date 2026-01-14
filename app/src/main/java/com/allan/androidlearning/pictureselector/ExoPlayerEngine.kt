package com.allan.androidlearning.pictureselector

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectorProviders
import com.luck.picture.lib.engine.VideoPlayerEngine
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnPlayerListener
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.indices

/**
 * @author：luck
 * @date：2022/7/1 22:26 下午
 * @describe：ExoPlayerEngine (Media3 适配版)
 */
class ExoPlayerEngine : VideoPlayerEngine<PlayerView?> {
    
    /**
     * 播放状态监听器集
     */
    private val listeners = CopyOnWriteArrayList<OnPlayerListener>()

    override fun onCreateVideoPlayer(context: Context): View {
        // 替换 StyledPlayerView 为 Media3 的 PlayerView
        val exoPlayer = PlayerView(context)
        exoPlayer.setUseController(false)
        return exoPlayer
    }

    override fun onStarPlayer(player: PlayerView?, media: LocalMedia) { 
        val player = player?.player
        if (player != null) {
            val mediaItem: MediaItem?
            val path = media.getAvailablePath()
            mediaItem = if (PictureMimeType.isContent(path)) {
                MediaItem.fromUri(path.toUri())
            } else if (PictureMimeType.isHasHttp(path)) {
                MediaItem.fromUri(path)
            } else {
                MediaItem.fromUri(Uri.fromFile(File(path)))
            }
            val config = SelectorProviders.getInstance().selectorConfig
            player.repeatMode = if (config.isLoopAutoPlay) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    override fun onResume(player: PlayerView?) {
        player?.player?.play()
    }

    override fun onPause(player: PlayerView?) {
        player?.player?.pause()
    }

    override fun isPlaying(player: PlayerView?): Boolean {
        return player?.player?.isPlaying == true
    }

    override fun addPlayListener(playerListener: OnPlayerListener?) {
        if (!listeners.contains(playerListener)) {
            listeners.add(playerListener)
        }
    }

    override fun removePlayListener(playerListener: OnPlayerListener?) {
        if (playerListener != null) {
            listeners.remove(playerListener)
        } else {
            listeners.clear()
        }
    }

    override fun onPlayerAttachedToWindow(player: PlayerView?) {
        player ?: return

        val p: Player = ExoPlayer.Builder(player.context).build()
        player.setPlayer(p)
        p.addListener(mPlayerListener)
    }

    override fun onPlayerDetachedFromWindow(player: PlayerView?) {
        val p = player?.player
        if (p != null) {
            p.removeListener(mPlayerListener)
            p.release()
            player.setPlayer(null)
        }
    }

    override fun destroy(player: PlayerView?) {
        val p = player?.getPlayer()
        if (p != null) {
            p.removeListener(mPlayerListener)
            p.release()
        }
    }

    /**
     * ExoPlayer播放状态回调 (Media3 版本)
     */
    private val mPlayerListener: Player.Listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            for (i in listeners.indices) {
                val playerListener = listeners.get(i)
                playerListener.onPlayerError()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                for (i in listeners.indices) {
                    val playerListener = listeners.get(i)
                    playerListener.onPlayerReady()
                }
            } else if (playbackState == Player.STATE_BUFFERING) {
                for (i in listeners.indices) {
                    val playerListener = listeners.get(i)
                    playerListener.onPlayerLoading()
                }
            } else if (playbackState == Player.STATE_ENDED) {
                for (i in listeners.indices) {
                    val playerListener = listeners.get(i)
                    playerListener.onPlayerEnd()
                }
            }
        }
    }
}