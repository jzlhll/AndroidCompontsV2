package childmonitor.platform

/** 单调时钟仅用于当前进程内计时，不能作为重启后的恢复基准。 */
fun interface MonitorClock {
    fun monotonicUs(): Long
}
