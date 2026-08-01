package com.au.module_androiduiex.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 对应 XML StyleI8oShadowWhiteBlock 的 Compose 阴影白底块。 */
@Composable
fun Modifier.composeShadowWhiteBlock(
    cornerRadius: Dp = 16.dp,
    strokeWidth: Dp = 1.dp,
    shadowColor: Color = ComposeColors.ShadowDefault,
    shadowBlurRadius: Dp = 32.dp,
    shadowSpread: Dp = 1.dp,
    backgroundColor: Color = ComposeColors.WhiteBlockBackground,
    strokeColor: Color = Color.White,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return composeWhiteBlockFrame(
        shape = shape,
        cornerRadius = cornerRadius,
        strokeWidth = strokeWidth,
        shadowColor = shadowColor,
        shadowBlurRadius = shadowBlurRadius,
        shadowSpread = shadowSpread,
        strokeColor = strokeColor,
        bodyModifier = Modifier.background(backgroundColor, shape),
    )
}

/** 绘制无偏移的模糊阴影。 */
fun Modifier.composeCenteredBlurShadow(
    cornerRadius: Dp = 24.dp,
    shadowColor: Color = Color(0x14000000),
    blurRadius: Dp = 32.dp,
    shadowSpread: Dp = 0.dp,
    shadowInset: Dp = 0.dp,
): Modifier {
    return composeOffsetBlurShadow(
        cornerRadius = cornerRadius,
        shadowColor = shadowColor,
        blurRadius = blurRadius,
        shadowSpread = shadowSpread,
        shadowInset = shadowInset,
    )
}

/** 绘制可配置偏移、扩散与模糊半径的阴影。 */
fun Modifier.composeOffsetBlurShadow(
    cornerRadius: Dp = 24.dp,
    shadowColor: Color = ComposeColors.RoundedCardShadow,
    blurRadius: Dp = 10.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 12.dp,
    shadowSpread: Dp = 0.dp,
    shadowInset: Dp = 0.dp,
): Modifier {
    return drawWithCache {
        val radiusPx = cornerRadius.toPx()
        val blurPx = blurRadius.toPx()
        val offsetXPx = offsetX.toPx()
        val offsetYPx = offsetY.toPx()
        val spreadPx = shadowSpread.toPx()
        val insetPx = shadowInset.toPx()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = shadowColor.toArgb()
            maskFilter = android.graphics.BlurMaskFilter(
                blurPx,
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }

        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    offsetXPx + insetPx - spreadPx,
                    offsetYPx + insetPx - spreadPx,
                    size.width + offsetXPx - insetPx + spreadPx,
                    size.height + offsetYPx - insetPx + spreadPx,
                    radiusPx,
                    radiusPx,
                    paint,
                )
            }
        }
    }
}

/** 保存 Compose 背景采样层与模糊渲染层。 */
@Stable
class ComposeBackdropBlurState internal constructor(
    internal val sourceLayer: GraphicsLayer,
    internal val blurredLayer: GraphicsLayer,
) {
    internal var sourcePositionInWindow by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberComposeBackdropBlurState(): ComposeBackdropBlurState {
    val sourceLayer = rememberGraphicsLayer()
    val blurredLayer = rememberGraphicsLayer()
    return remember(sourceLayer, blurredLayer) {
        ComposeBackdropBlurState(sourceLayer, blurredLayer)
    }
}

/** 记录需要被背景模糊组件采样的 Compose 内容。 */
fun Modifier.composeBackdropBlurSource(state: ComposeBackdropBlurState): Modifier {
    return onGloballyPositioned { coordinates ->
        state.sourcePositionInWindow = coordinates.positionInWindow()
    }.drawWithContent {
        state.sourceLayer.record {
            this@drawWithContent.drawContent()
        }
        state.blurredLayer.record(size = state.sourceLayer.size) {
            drawLayer(state.sourceLayer)
        }
        drawLayer(state.sourceLayer)
    }
}

/** 带背景模糊、半透明白底、描边和阴影的 Compose 浮层。 */
@Composable
fun ComposeBackdropBlurWhiteBlock(
    state: ComposeBackdropBlurState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    backgroundBlurRadius: Dp = 16.dp,
    strokeWidth: Dp = 1.dp,
    shadowColor: Color = Color(0x14000000),
    shadowBlurRadius: Dp = 32.dp,
    shadowSpread: Dp = 0.dp,
    backgroundColor: Color = ComposeColors.White80Percent,
    strokeColor: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurRadiusPx = with(LocalDensity.current) { backgroundBlurRadius.toPx() }
    val blurEffect = remember(blurRadiusPx) {
        BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
    }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    var targetPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    SideEffect {
        state.blurredLayer.renderEffect = if (blurEffect.isSupported()) blurEffect else null
    }

    Box(
        modifier = modifier.composeWhiteBlockFrame(
            shape = shape,
            cornerRadius = cornerRadius,
            strokeWidth = strokeWidth,
            shadowColor = shadowColor,
            shadowBlurRadius = shadowBlurRadius,
            shadowSpread = shadowSpread,
            strokeColor = strokeColor,
            bodyModifier = Modifier
                .clip(shape)
                .onGloballyPositioned { coordinates ->
                    targetPositionInWindow = coordinates.positionInWindow()
                }
                .drawWithCache {
                    val sourceOffset = state.sourcePositionInWindow - targetPositionInWindow
                    onDrawBehind {
                        if (state.blurredLayer.size.width > 0 && state.blurredLayer.size.height > 0) {
                            withTransform({
                                translate(sourceOffset.x, sourceOffset.y)
                            }) {
                                drawLayer(state.blurredLayer)
                            }
                        }
                        drawRect(backgroundColor)
                    }
                },
        ),
        content = content,
    )
}

private fun Modifier.composeWhiteBlockFrame(
    shape: RoundedCornerShape,
    cornerRadius: Dp,
    strokeWidth: Dp,
    shadowColor: Color,
    shadowBlurRadius: Dp,
    shadowSpread: Dp,
    strokeColor: Color,
    bodyModifier: Modifier,
): Modifier {
    return composeCenteredBlurShadow(
        cornerRadius = cornerRadius,
        shadowColor = shadowColor,
        blurRadius = shadowBlurRadius,
        shadowSpread = shadowSpread,
        shadowInset = strokeWidth / 2,
    )
        .then(bodyModifier)
        .border(strokeWidth, strokeColor, shape)
}

/**
 * 将调用方绘制的背景内容裁成圆形并模糊，再覆盖一层保持清晰边界的半透明颜色。
 */
@Composable
fun Modifier.composeBlurredCircleBackground(
    blurRadius: Dp = 4.dp,
    overlayColor: Color = ComposeColors.TextDesc50Percent,
): Modifier {
    return clip(CircleShape)
        .drawWithContent {
            drawContent()
            drawRect(overlayColor)
        }
        .blur(blurRadius)
}
