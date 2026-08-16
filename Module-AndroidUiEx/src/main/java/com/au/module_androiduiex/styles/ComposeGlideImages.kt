package com.au.module_androiduiex.styles

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * 返回用于将 Compose 图像转换为灰度的颜色滤镜。
 */
fun grayscaleColorFilter(): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) },
)

/**
 * 使用 Glide 加载固定宽高圆角图片。
 */
@Composable
fun GlideRoundedImage(
    model: Any?,
    contentDescription: String?,
    width: Dp,
    height: Dp = width,
    cornerRadius: Dp = 0.dp,
    modifier: Modifier = Modifier,
    @DrawableRes placeholderResId: Int? = null,
    @DrawableRes errorResId: Int? = placeholderResId,
    diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    logicalImageKey: Any? = model,
    requestKey: Any? = null,
    requestWidth: Dp = width,
    requestHeight: Dp = height,
    onResourceDisplayed: (() -> Unit)? = null,
    requestBuilderTransform: ((RequestBuilder<Drawable>) -> RequestBuilder<Drawable>)? = null,
) {
    val context = LocalContext.current
    val requestManager = remember(context) { Glide.with(context) }
    val widthPx = with(LocalDensity.current) { requestWidth.roundToPx() }
    val heightPx = with(LocalDensity.current) { requestHeight.roundToPx() }
    val state = remember(logicalImageKey) { GlideRoundedImageState() }
    val currentRequestBuilderTransform by rememberUpdatedState(requestBuilderTransform)
    val currentOnResourceDisplayed by rememberUpdatedState(onResourceDisplayed)

    val displayedResource = if (model == null) null else state.displayedResource
    val displayedTarget = displayedResource?.target
    LaunchedEffect(displayedTarget) {
        if (displayedTarget != null) {
            withFrameNanos { }
            currentOnResourceDisplayed?.invoke()
        }
    }
    DisposableEffect(displayedTarget, requestManager) {
        onDispose {
            displayedTarget?.let(requestManager::clear)
        }
    }

    DisposableEffect(
        state,
        model,
        requestKey,
        widthPx,
        heightPx,
        diskCacheStrategy,
        contentScale,
        requestManager,
    ) {
        if (model == null) {
            onDispose {}
        } else {
            state.pendingTarget?.let(requestManager::clear)
            val target = object : CustomTarget<Drawable>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?,
                ) {
                    if (state.pendingTarget !== this && state.displayedResource?.target !== this) return
                    state.displayedResource = DisplayedGlideResource(resource, this)
                    state.failed = false
                    if (state.pendingTarget === this) {
                        state.pendingTarget = null
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (state.pendingTarget !== this) return
                    if (state.displayedResource == null) {
                        state.failed = true
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    if (state.pendingTarget === this) {
                        state.pendingTarget = null
                    }
                    if (state.displayedResource?.target === this) {
                        state.displayedResource = null
                    }
                }
            }
            state.pendingTarget = target
            val scaleBuilder = when (contentScale) {
                ContentScale.Crop -> requestManager.load(model).optionalCenterCrop()
                ContentScale.Inside,
                ContentScale.Fit -> requestManager.load(model).optionalCenterInside()
                else -> requestManager.load(model)
            }
            val builder = scaleBuilder
                .diskCacheStrategy(diskCacheStrategy)
                .override(widthPx, heightPx)
            val transformedBuilder = currentRequestBuilderTransform?.invoke(builder) ?: builder
            transformedBuilder.into(target)

            onDispose {
                if (state.pendingTarget === target) {
                    state.pendingTarget = null
                    requestManager.clear(target)
                }
            }
        }
    }

    val fallbackResId = if (model != null && state.failed) errorResId ?: placeholderResId else placeholderResId
    val fallbackDrawable = fallbackResId?.let { resId ->
        remember(context, resId) { ContextCompat.getDrawable(context, resId) }
    }
    var imageModifier = modifier.size(width = width, height = height)
    if (cornerRadius > 0.dp) {
        imageModifier = imageModifier.clip(RoundedCornerShape(cornerRadius))
    }
    Image(
        painter = rememberDrawablePainter(displayedResource?.drawable ?: fallbackDrawable),
        contentDescription = contentDescription,
        modifier = imageModifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}

private data class DisplayedGlideResource(
    val drawable: Drawable,
    val target: CustomTarget<Drawable>,
)

private class GlideRoundedImageState {
    var displayedResource by mutableStateOf<DisplayedGlideResource?>(null)
    var failed by mutableStateOf(false)
    var pendingTarget: CustomTarget<Drawable>? = null
}
