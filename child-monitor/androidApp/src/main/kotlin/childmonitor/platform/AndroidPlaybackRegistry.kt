package childmonitor.platform

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只注册可撤销播放器句柄，删除前释放同会话的所有播放器。 */
class AndroidPlaybackRegistry : PlaybackRegistry {
    private val players = mutableMapOf<String, Pair<String, ExoPlayer>>()
    fun register(instanceId: String, sessionId: String, player: ExoPlayer) { players[instanceId] = sessionId to player }
    fun remove(instanceId: String) { players.remove(instanceId)?.second?.release() }
    override suspend fun releaseSession(sessionId: String) = withContext(Dispatchers.Main.immediate) {
        players.filterValues { it.first == sessionId }.keys.toList().forEach(::remove)
    }
}
