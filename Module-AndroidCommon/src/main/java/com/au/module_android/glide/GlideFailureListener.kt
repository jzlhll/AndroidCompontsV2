package com.au.module_android.glide

import com.au.module_android.log.logEx
import com.au.module_android.log.loge
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.target.ViewTarget

/** 业务来源切换可以经失败回调交接，但不属于加载错误。 */
interface ExpectedGlideFailure {
    val isExpectedGlideFailure: Boolean
}

/**  区分缓存探测、正常来源切换与真实失败，避免 Glide 将预期分支统一打印为异常。 */
class GlideFailureListener<T : Any>(private val cacheOnly: Boolean = false) : RequestListener<T> {
    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<T>, isFirstResource: Boolean): Boolean {
        val causes = e?.rootCauses.orEmpty()
        if (cacheOnly && e != null && causes.isEmpty()) return false
        val failures = causes.filterNot { it is ExpectedGlideFailure && it.isExpectedGlideFailure }
        if (causes.isNotEmpty() && failures.isEmpty()) return false
        val owner = (target as? ViewTarget<*, *>)?.view ?: target
        val detail = "load failed owner=${System.identityHashCode(owner)}, model=$model, cacheOnly=$cacheOnly"
        if (failures.isNotEmpty()) {
            // GlideException 自身没有调用栈，显式记录真实根因，避免只留下错误摘要。
            failures.forEachIndexed { index, cause ->
                logEx("GlideFailure", throwable = cause) {
                    "$detail, root=${index + 1}/${failures.size}, cause=${cause.javaClass.name}"
                }
            }
        } else if (e != null) {
            logEx("GlideFailure", throwable = e) { detail }
        } else {
            loge("GlideFailure") { detail }
        }
        return false
    }

    override fun onResourceReady(resource: T, model: Any, target: Target<T>?, dataSource: DataSource, isFirstResource: Boolean): Boolean = false
}
