package childmonitor.platform

interface ReminderPlayer {
    suspend fun play(audioId: String, onStarted: () -> Unit): Boolean
}
