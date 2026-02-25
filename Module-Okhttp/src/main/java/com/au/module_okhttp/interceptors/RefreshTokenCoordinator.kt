package com.au.module_okhttp.interceptors

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 刷新token单例协调器：保证并发场景下仅有一次刷新执行，其他等待复用结果
 * 通过Mutex保护mInFlight的创建与读写，首个调用者创建刷新任务，其他并发调用者复用并等待该任务结果
 */
object RefreshTokenCoordinator {
    /** 同步锁，用于串行化检查与设置mInFlight以及最近成功状态，避免并发下重复创建刷新任务 */
    private val mMutex = Mutex()
    /** 当前在进行的刷新任务占位；存在时并发调用者直接await其结果，完成后统一分发并置空 */
    private var mInFlight: CompletableDeferred<Result<Boolean>>? = null
    private var mLastSuccessAt: Long = 0L
    private var mIsLastSuccess: Boolean = false
    private const val REUSE_WINDOW_MS: Long = 3000

    suspend fun awaitOrRun(block: suspend () -> Boolean): Boolean {
        /** 检查短时复用窗口：若刚刚刷新成功且仍在窗口内，直接复用成功结果 */
        val reuse = mMutex.withLock {
            mIsLastSuccess && (System.currentTimeMillis() - mLastSuccessAt) <= REUSE_WINDOW_MS
        }

        if (reuse) {
            return true
        }

        /** 获取或创建进行中的刷新占位：首个调用者创建，其余并发调用者拿到waiter并等待 */
        val waiter = mMutex.withLock {
            val current = mInFlight
            if (current != null) {
                current
            } else {
                val created = CompletableDeferred<Result<Boolean>>()
                mInFlight = created
                null
            }
        }

        /** 并发等待路径：已有刷新任务则等待其完成并复用结果 */
        if (waiter != null) {
            val r = waiter.await()
            return r.getOrDefault(false)
        }

        try {
            /** 首个调用者执行实际刷新逻辑 */
            val ok = block()
            mMutex.withLock {
                /** 广播成功结果并清理占位，记录最近成功时间用于短时复用 */
                mInFlight?.complete(Result.success(ok))
                mInFlight = null
                if (ok) {
                    mIsLastSuccess = true
                    mLastSuccessAt = System.currentTimeMillis()
                }
            }
            return ok
        } catch (t: Throwable) {
            mMutex.withLock {
                /** 广播失败结果并清理占位，关闭复用标记 */
                mInFlight?.complete(Result.failure(t))
                mInFlight = null
                mIsLastSuccess = false
            }
            throw t
        }
    }
}
