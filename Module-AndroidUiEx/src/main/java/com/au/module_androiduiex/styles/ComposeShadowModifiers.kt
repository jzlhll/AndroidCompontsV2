package com.au.module_androiduiex.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private val ComposeShadowBlockRadius = 16.dp
private val ComposeShadowBlockShape = RoundedCornerShape(ComposeShadowBlockRadius)
private val ComposeShadowBlockStroke = 1.dp
private val ComposeShadowBlockSpread = 1.dp
private val ComposeShadowBlockBlur = 32.dp

@Composable
fun Modifier.composeShadowWhiteBlock(): Modifier {
    val shadowColor = ComposeColors.ShadowDefault.toArgb()
    val backgroundColor = ComposeColors.BackgroundBg
    return drawWithCache {
        val radiusPx = ComposeShadowBlockRadius.toPx()
        val strokePx = ComposeShadowBlockStroke.toPx()
        val spreadPx = ComposeShadowBlockSpread.toPx()
        val blurPx = ComposeShadowBlockBlur.toPx()
        val inset = strokePx / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = shadowColor
            maskFilter = android.graphics.BlurMaskFilter(
                blurPx,
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }

        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    inset - spreadPx,
                    inset - spreadPx,
                    size.width - inset + spreadPx,
                    size.height - inset + spreadPx,
                    radiusPx,
                    radiusPx,
                    paint,
                )
            }
        }
    }
        .background(backgroundColor, ComposeShadowBlockShape)
        .border(ComposeShadowBlockStroke, Color.White, ComposeShadowBlockShape)
}

/** 卡片外层轻阴影，对应 Figma Default drop shadow。 */
fun Modifier.composeCollectionCardShadow(): Modifier = drawWithCache {
    val blurPx = 10.dp.toPx()
    val offsetYPx = 12.dp.toPx()
    val radiusPx = 24.dp.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = Color(0x0A404040).toArgb()
        maskFilter = android.graphics.BlurMaskFilter(
            blurPx,
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                0f,
                offsetYPx,
                size.width,
                size.height + offsetYPx,
                radiusPx,
                radiusPx,
                paint,
            )
        }
    }
}
