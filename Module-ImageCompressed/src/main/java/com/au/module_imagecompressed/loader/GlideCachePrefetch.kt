package com.au.module_imagecompressed.loader

import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 等待已配置的请求完成缓存预热，结束或取消时释放本次 Target，不向外移交图片所有权。
 * 来源、签名、远程访问和优先级由调用方配置，返回是否加载成功。
 */
suspend fun RequestManager.prefetchImageCache(
    request: RequestBuilder<Drawable>,
): Boolean = withContext(Dispatchers.Main) {
    currentCoroutineContext().ensureActive()
    val completion = CompletableDeferred<Boolean>()
    val target = object : CustomTarget<Drawable>() {
        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            completion.complete(true)
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            completion.complete(false)
        }

        override fun onLoadCleared(placeholder: Drawable?) {
            completion.complete(false)
        }
    }
    try {
        request.into(target)
        completion.await()
    } finally {
        clear(target)
    }
}
