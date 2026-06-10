package com.au.module_androiduiex.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private val ShadowBlockRadius = 16.dp
private val ShadowBlockShape = RoundedCornerShape(ShadowBlockRadius)
private val ShadowBlockStroke = 1.dp
private val ShadowBlockSpread = 1.dp
private val ShadowBlockBlur = 32.dp

/** 阴影白底块。 */
fun Modifier.shadowWhiteBlock(): Modifier = drawWithCache {
    val radiusPx = ShadowBlockRadius.toPx()
    val strokePx = ShadowBlockStroke.toPx()
    val spreadPx = ShadowBlockSpread.toPx()
    val blurPx = ShadowBlockBlur.toPx()
    val inset = strokePx / 2f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = UiExColors.ShadowDefault.toArgb()
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
    .background(UiExColors.WhiteBlockBackground, ShadowBlockShape)
    .border(ShadowBlockStroke, Color.White, ShadowBlockShape)

/** 卡片外层轻阴影。 */
fun Modifier.collectionCardShadow(): Modifier = drawWithCache {
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
