package com.au.module_androiduiex.styles

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * 使用 Glide 加载固定宽高圆角图片。
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun GlideRoundedImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp = width,
    cornerRadius: Dp,
    @DrawableRes placeholderResId: Int? = null,
    @DrawableRes errorResId: Int? = placeholderResId,
    diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC,
    contentScale: ContentScale = ContentScale.Crop,
    requestBuilderTransform: ((RequestBuilder<Drawable>) -> RequestBuilder<Drawable>)? = null,
) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val heightPx = with(LocalDensity.current) { height.roundToPx() }
    val imageModifier = modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(cornerRadius))

    if (placeholderResId == null && errorResId == null) {
        GlideImage(
            model = model,
            contentDescription = contentDescription,
            modifier = imageModifier,
            contentScale = contentScale,
        ) { requestBuilder ->
            val builder = requestBuilder
                .diskCacheStrategy(diskCacheStrategy)
                .override(widthPx, heightPx)
            requestBuilderTransform?.invoke(builder) ?: builder
        }
    } else {
        GlideImage(
            model = model,
            contentDescription = contentDescription,
            modifier = imageModifier,
            contentScale = contentScale,
            loading = placeholderResId?.let { placeholder(it) },
            failure = errorResId?.let { placeholder(it) },
        ) { requestBuilder ->
            val builder = requestBuilder
                .diskCacheStrategy(diskCacheStrategy)
                .override(widthPx, heightPx)
            requestBuilderTransform?.invoke(builder) ?: builder
        }
    }
}
