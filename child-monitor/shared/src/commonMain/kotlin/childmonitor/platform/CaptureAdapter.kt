package childmonitor.platform

import childmonitor.model.EndReason
import childmonitor.model.Observation

interface CaptureAdapter {
    suspend fun start(sessionId: String, generation: Long, stagingPath: String,
        onObservation: (Observation) -> Unit, onInterrupted: (EndReason) -> Unit): Long
    suspend fun stop(): CaptureFinish
}
data class CaptureFinish(val released: Boolean, val finalized: Boolean)

interface RuntimeClock {
    fun monotonicUs(): Long
    fun wallUs(): Long
    fun timezoneId(): String
    fun newId(): String
}
