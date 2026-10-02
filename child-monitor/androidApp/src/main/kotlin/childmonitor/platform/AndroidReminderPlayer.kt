package childmonitor.platform

import android.content.Context
import kotlin.time.Duration.Companion.milliseconds
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 固定语音单次播放，协程取消时立即释放播放器和音频焦点。 */
class AndroidReminderPlayer(private val context: Context) : ReminderPlayer {
    override suspend fun play(audioId: String, onStarted: () -> Unit, volume: Float): Boolean = withContext(Dispatchers.Main.immediate) {
        if (audioId == "reminder_target") {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, (volume * 100).toInt())
            try {
                onStarted()
                tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 600)
                kotlinx.coroutines.delay(700.milliseconds)
                return@withContext true
            } finally { tone.release() }
        }
        require(audioId.matches(Regex("reminder_[a-z_]+")))
        val finished = CompletableDeferred<Boolean>()
        val player = ExoPlayer.Builder(context).build()
        var reported = false
        try {
            player.volume = volume
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && !reported) { reported = true; onStarted() }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) finished.complete(true)
                }
                override fun onPlayerError(error: PlaybackException) { finished.complete(false) }
            })
            player.setMediaItem(MediaItem.fromUri("asset:///voice/$audioId.m4a"))
            player.prepare()
            player.play()
            finished.await()
        } finally { player.release() }
    }
}
