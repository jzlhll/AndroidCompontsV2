package com.au.module_android.glide

import android.content.Context
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toDrawable
import com.au.module_android.Globals
import com.au.module_android.R
import com.au.module_android.utils.deleteAll
import com.au.module_android.utils.ignoreError
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.bitmap.VideoDecoder
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.MediaStoreSignature
import kotlinx.coroutines.delay
import java.io.File

/**
 * 清除或者取消加载
 */
fun ImageView.clearByGlide() {
    Glide.with(this).clear(this)
}

/**
 * 清除磁盘缓存
 */
fun clearGlideImageDiskCache() {
    ignoreError {
        Glide.get(Globals.app).clearDiskCache()
    }
}

@WorkerThread
fun clearAppFileDir() {
    ignoreError {
        Globals.app.filesDir?.deleteAll()
        Globals.app.getExternalFilesDir(null)?.deleteAll()
    }
}

/**
 * 第二个参数，可以对现有RequestOptions进行二次处理。
 * 去除inline避免膨胀代码。
 *
 * 内部使用
 */
fun ImageView.glideSetAny(
    load: Any?,
    optionsTransform: ((RequestOptions)-> RequestOptions)? = null
) {
    load ?: return
    if (load is String && load.isEmpty()) return
    //对于String的形式，会定制cache key。
    val manager = Glide.with(this)
    val opt = RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.AUTOMATIC)
    val options = optionsTransform?.invoke(opt) ?: opt
    manager.load(load).apply(options).into(this)
}

fun ImageView.glideSetAnyWithDefault(
    load: String?,
    @ColorInt colorGray:Int? = null
) {
    val c = colorGray ?: Globals.getColor(R.color.color_glide_gray_default)
    val resInt = c.toDrawable()
    if (load == null) {
        setImageDrawable(resInt)
        return
    }

    glideSetAny(load) {
        it.error(resInt).placeholder(resInt)
    }
}

fun ImageView.glideSetAnyWithResDefault(
    load: String?,
    resId: Int,
) {
    if (load == null) {
        setImageResource(resId)
        return
    }
    glideSetAny(load) {
        it.error(resId).placeholder(resId)
    }
}

/**
 * 加载本地文件
 */
fun ImageView.glideLoadFile(localFile: File, errorDrawableId:Int,
                            isScrollingFast: Boolean = false,
                            cacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC) {
    Glide.with(context)
        .load(localFile)
        .diskCacheStrategy(cacheStrategy) // 本地文件无需磁盘缓存
        .skipMemoryCache(false) // 保留内存缓存提升滑动流畅性
        .error(errorDrawableId)
        // 滑动时降低加载优先级
        .priority(if (isScrollingFast) Priority.LOW else Priority.HIGH)
        .into(this)
}

/**
 * 设置圆形图片
 */
fun ImageView.glideSetAnyAsCircleCrop(load: Any?) {
    val manager = Glide.with(this)
    if (load == null || (load is String && load.isBlank())) {
        manager.clear(this)
        return
    }
    val options = RequestOptions.bitmapTransform(CircleCrop()).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    manager.load(load).apply(options).into(this)
}

/**
 * 设置圆角图片
 */
fun ImageView.glideSetAnyAsRoundedCorners(
    load: Any?,
    roundingRadius: Int,
) {
    val manager = Glide.with(this)
    if (load == null || (load is String && load.isBlank())) {
        manager.clear(this)
        return
    }

    val options = RequestOptions.bitmapTransform(
        RoundedCorners(
            roundingRadius
        )
    ).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
    manager.load(load).apply(options).into(this)
}

/**
 * 加载视频第一帧
 */
fun ImageView.glideLoadVideoFirstFrame(url: Any, sizeCall: Function3<Drawable, Int, Int, Unit>? = null) {
    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        //获得第1帧图片 这里的第一个参数 以微秒为单位
        .frame(0)
        .set(VideoDecoder.FRAME_OPTION, MediaMetadataRetriever.OPTION_CLOSEST)
    Glide.with(context)
        .setDefaultRequestOptions(options)
        .load(url)
        .into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable>?
            ) {
                sizeCall?.invoke(resource, resource.intrinsicWidth, resource.intrinsicHeight)
            }

            override fun onLoadCleared(placeholder: Drawable?) {

            }
        })
}

// 封装：根据Content URI获取媒体库元数据，并加载图片，并带签名确保缓存更新
suspend fun ImageView.glideLoadContentUri(contentUri: Uri) {
    delay(0)
    val context = Globals.app
    // 1. 查询该Content URI对应的元数据（MIME类型、修改时间、旋转角度）
    val (mimeType, dateModified, orientation) = getMediaStoreMeta(context, contentUri)

    // 2. 创建MediaStoreSignature（核心：元数据变，签名就变）
    val signature = MediaStoreSignature(mimeType, dateModified, orientation)

    // 3. 加载Content URI并绑定签名
    Glide.with(context)
        .load(contentUri) // 传入Content URI
        .signature(signature) // 绑定媒体库签名
        .into(this)
}

// 工具方法：从ContentResolver查询元数据
private fun getMediaStoreMeta(context: Context, contentUri: Uri): Triple<String, Long, Int> {
    val contentResolver = context.contentResolver
    val projection = arrayOf(
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.ORIENTATION
    )

    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(contentUri, projection, null, null, null)
        if (cursor?.moveToFirst() == true) {
            // 获取MIME类型（默认image/jpeg）
            val mimeType = cursor.getString(0) ?: "image/jpeg"
            // 获取最后修改时间戳（秒）
            val dateModified = cursor.getLong(1)
            // 获取旋转角度（默认0）
            val orientation = cursor.getInt(2)
            return Triple(mimeType, dateModified, orientation)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        cursor?.close()
    }
    // 兜底默认值
    return Triple("image/jpeg", 0L, 0)
}

// 示例调用
// val contentUri = Uri.parse("content://media/external/images/media/1001")
// loadMediaStoreImage(context, imageView, contentUri)

/**
 * 有的支持替换图片资源的size。比如aliyun裁剪服务等。
 */
fun resizeImgUrl(url:String?, size:String):String? {
    if (url == null) {
        return null
    }

    return url.replace(".png", "_${size}.png")
        .replace(".jpg", "_${size}.jpg").replace(".JPG", "_${size}.JPG")
        .replace(".PNG", "_${size}.PNG")
}
