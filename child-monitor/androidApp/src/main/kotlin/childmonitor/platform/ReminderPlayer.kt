package childmonitor.platform

interface ReminderPlayer {
    suspend fun play(audioId: String, onStarted: () -> Unit, volume: Float = 1f): Boolean
}
