package childmonitor.platform

fun interface PlaybackRegistry { suspend fun releaseSession(sessionId: String) }
