package com.allan.mydroid.views.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.au.module_androiduiex.preview.AppPreview
import com.au.module_androiduiex.styles.ComposeImagePreviewContainer
import com.au.module_androiduiex.styles.ComposeImmersiveImageBackground
import com.au.module_androiduiex.styles.GlideRoundedImage
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy

/** 图片等比贴合窗口的一条边并居中，剩余区域通过同一图片的低分辨率模糊背景填充。 */
@Composable
fun SendImagePreviewScreen(
    model: Any?,
    contentDescription: String?,
    onClose: () -> Unit,
    onLoadFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var imageAspectRatio by remember(model) { mutableFloatStateOf(0f) }
    var loadFailed by remember(model) { mutableStateOf(false) }

    LaunchedEffect(model, loadFailed) {
        if (loadFailed) onLoadFailed()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        if (windowWidth <= 0.dp || windowHeight <= 0.dp) return@BoxWithConstraints

        ComposeImmersiveImageBackground(
            width = windowWidth,
            height = windowHeight,
            modifier = Modifier.matchParentSize(),
        ) { requestWidth, requestHeight, transformation ->
            GlideRoundedImage(
                model = model,
                contentDescription = null,
                width = windowWidth,
                height = windowHeight,
                modifier = Modifier.fillMaxSize(),
                requestWidth = requestWidth,
                requestHeight = requestHeight,
                diskCacheStrategy = DiskCacheStrategy.NONE,
                contentScale = ContentScale.Crop,
            ) { it.format(DecodeFormat.PREFER_ARGB_8888).transform(transformation) }
        }

        ComposeImagePreviewContainer(
            previewKey = model,
            imageAspectRatio = if (imageAspectRatio > 0f) {
                imageAspectRatio
            } else {
                windowWidth.value / windowHeight.value
            },
            initialTopPadding = 0.dp,
            initialHorizontalPadding = 0.dp,
            initialBottomPadding = 0.dp,
            modifier = Modifier.fillMaxSize(),
            onSingleTap = onClose,
        ) { imageWidth, imageHeight ->
            GlideRoundedImage(
                model = model,
                contentDescription = contentDescription,
                width = imageWidth,
                height = imageHeight,
                // 固定按窗口请求，得到真实比例后调整布局时无需重新解码前景。
                requestWidth = windowWidth,
                requestHeight = windowHeight,
                diskCacheStrategy = DiskCacheStrategy.NONE,
                contentScale = ContentScale.Fit,
                onResourceReady = { drawable ->
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        imageAspectRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                    }
                },
                onResourceFailed = { loadFailed = true },
            ) { it.format(DecodeFormat.PREFER_ARGB_8888) }
        }
    }
}

@Preview(name = "Portrait", widthDp = 360, heightDp = 640)
@Preview(name = "Landscape", widthDp = 640, heightDp = 360)
@Composable
private fun SendImagePreviewScreenPreview() {
    AppPreview {
        SendImagePreviewScreen(
            model = android.R.drawable.ic_menu_gallery,
            contentDescription = null,
            onClose = {},
            onLoadFailed = {},
            modifier = Modifier.background(MyDroidComposeColors.ImagePreviewBackground),
        )
    }
}
