package childmonitor.platform

import androidx.media3.exoplayer.ExoPlayer
import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只注册可撤销播放器句柄，删除前释放同会话的所有播放器。 */
class AndroidPlaybackRegistry : PlaybackRegistry {
    private val players = mutableMapOf<String, Pair<String, ExoPlayer>>()
    fun register(instanceId: String, sessionId: String, player: ExoPlayer) {
        players[instanceId] = sessionId to player
        logdNoFile(tag = TAG) { "player registered sessionId=$sessionId playerInstanceId=$instanceId count=${players.size}" }
    }
    fun remove(instanceId: String) {
        val entry = players.remove(instanceId) ?: return
        entry.second.release()
        logdNoFile(tag = TAG) { "player released sessionId=${entry.first} playerInstanceId=$instanceId count=${players.size}" }
    }
    override suspend fun releaseSession(sessionId: String) = withContext(Dispatchers.Main.immediate) {
        players.filterValues { it.first == sessionId }.keys.toList().forEach(::remove)
    }
}
