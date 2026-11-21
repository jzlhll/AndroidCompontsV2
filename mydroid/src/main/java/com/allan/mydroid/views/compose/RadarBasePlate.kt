package com.allan.mydroid.views.compose

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp
import com.au.module_androiduiex.styles.ComposeColors

/** 雷达与主机槽位共用中心，只有 BLE 扫描期间才运行动画。 */
@Composable
fun RadarBasePlate(scanning: Boolean, modifier: Modifier = Modifier) {
    val phase = if (scanning) {
        rememberInfiniteTransition(label = "radar").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 4000, easing = LinearEasing)),
            label = "radarPhase",
        )
    } else {
        null
    }

    val color = ComposeColors.BlueLink
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
            val lineWidth = 1.dp.toPx()
            val renderer: AbsRadarRenderer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RadarRendererApi33(size, color, lineWidth)
            } else {
                RadarRendererApi29(size, color, lineWidth)
            }
            onDrawBehind { renderer.draw(this, phase?.value) }
        },
    )
}
