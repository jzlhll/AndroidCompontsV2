package com.au.module_androidui.ui

import android.app.Activity
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import com.au.module_android.init.GlobalActivityCallback
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.asOrNull
import kotlin.math.max

// 默认屏幕刷新率。
private const val DEFAULT_ACTIVITY_REFRESH_RATE = 60f

/**
 * 监控Activity显示耗时。
 *
 * @param activity 被监控的Activity。
 */
fun monitorActivityLoadTime(activity: Activity) {
    val name = activity.asOrNull<FragmentShellActivity>()?.fragmentClass?.simpleName ?: activity.javaClass.simpleName
    val startTime = System.currentTimeMillis()
    val contentView = activity.findViewById<View>(android.R.id.content)

    contentView.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                contentView.post {
                    val loadTime = System.currentTimeMillis() - startTime
                    logdNoFile(GlobalActivityCallback::class.java) {
                        "$name loadTime: $loadTime ms"
                    }
                }
            }
        }
    )
}

/**
 * 监控Fragment显示耗时。
 *
 * @param fragment 被监控的Fragment。
 */
fun monitorFragmentLoadTime(fragment: Fragment) {
    val name = fragment.javaClass.simpleName
    fragment.view?.let { view ->
        val startTime = System.currentTimeMillis()

        view.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    view.post {
                        val loadTime = System.currentTimeMillis() - startTime
                        logdNoFile(GlobalActivityCallback::class.java) {
                            "$name loadTime: $loadTime ms"
                        }
                    }
                }
            }
        )
    }
}

/**
 * 创建Activity帧率监控器。
 *
 * @param activity 被监控的Activity。
 * @return Activity帧率监控器。
 */
fun monitorActivityFrameRate(activity: Activity): ActivityFrameRateMonitor {
    val name = activity.asOrNull<FragmentShellActivity>()?.fragmentClass?.simpleName ?: activity.javaClass.simpleName
    val refreshRate = activity.window.decorView.display?.refreshRate ?: DEFAULT_ACTIVITY_REFRESH_RATE
    return ActivityFrameRateMonitor(name, refreshRate)
}

/** 监控Activity主线程帧回调并按秒输出日志。 */
class ActivityFrameRateMonitor(
    private val name: String,
    refreshRate: Float,
) : Choreographer.FrameCallback {

    // 是否正在监听帧回调。
    private var isStarted = false

    // 当前统计窗口的起始回调时间。
    private var windowStartNanos = 0L

    // 上一帧回调到达时间。
    private var lastFrameNanos = 0L

    // 当前统计窗口内的帧数。
    private var frameCount = 0

    // 当前统计窗口内疑似卡顿帧数量。
    private var jankFrameCount = 0

    // 当前统计窗口内估算丢帧数量。
    private var skippedFrameCount = 0

    // 当前统计窗口内最大帧间隔。
    private var maxFrameIntervalNanos = 0L

    // 当前设备刷新率对应的单帧间隔。
    private val expectedFrameIntervalNanos = (NANOS_PER_SECOND / if (refreshRate > 0f) refreshRate else DEFAULT_ACTIVITY_REFRESH_RATE).toLong()

    // 判定疑似卡顿帧的帧间隔阈值。
    private val jankFrameThresholdNanos = (expectedFrameIntervalNanos * JANK_FRAME_THRESHOLD_MULTIPLIER).toLong()

    /** 开始监控帧率。 */
    fun start() {
        if (isStarted) {
            return
        }
        isStarted = true
        resetWindow()
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** 停止监控帧率。 */
    fun stop() {
        if (!isStarted) {
            return
        }
        isStarted = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isStarted) {
            return
        }
        val nowNanos = System.nanoTime()
        if (windowStartNanos == 0L) {
            windowStartNanos = nowNanos
        }
        recordFrame(nowNanos)
        frameCount++

        val durationNanos = nowNanos - windowStartNanos
        if (durationNanos >= FRAME_RATE_LOG_INTERVAL_NANOS) {
            val fps = frameCount * NANOS_PER_SECOND.toFloat() / durationNanos
            val maxFrameMs = maxFrameIntervalNanos / NANOS_PER_MILLISECOND.toFloat()
            logdNoFile(GlobalActivityCallback::class.java) {
                "$name uiFps: ${"%.1f".format(fps)}, jank: $jankFrameCount, skipped: $skippedFrameCount, maxFrame: ${"%.1f".format(maxFrameMs)} ms"
            }
            resetWindow(nowNanos)
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    // 记录相邻帧回调间隔，用于识别主线程卡顿。
    private fun recordFrame(nowNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nowNanos
            return
        }
        val intervalNanos = nowNanos - lastFrameNanos
        lastFrameNanos = nowNanos
        maxFrameIntervalNanos = max(maxFrameIntervalNanos, intervalNanos)
        if (intervalNanos > jankFrameThresholdNanos) {
            jankFrameCount++
            skippedFrameCount += max(0, (intervalNanos / expectedFrameIntervalNanos).toInt() - 1)
        }
    }

    // 重置统计窗口。
    private fun resetWindow(startNanos: Long = 0L) {
        windowStartNanos = startNanos
        lastFrameNanos = startNanos
        frameCount = 0
        jankFrameCount = 0
        skippedFrameCount = 0
        maxFrameIntervalNanos = 0L
    }

    private companion object {
        // 帧率日志统计周期。
        private const val FRAME_RATE_LOG_INTERVAL_NANOS = 1_000_000_000L

        // 每秒纳秒数。
        private const val NANOS_PER_SECOND = 1_000_000_000L

        // 每毫秒纳秒数。
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        // 超过目标帧间隔1.5倍视为疑似卡顿。
        private const val JANK_FRAME_THRESHOLD_MULTIPLIER = 1.5f
    }
}