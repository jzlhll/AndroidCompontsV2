package com.allan.picnote.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.allan.picnote.data.ImportMode

private val PhotoPath = addPathNodes(
    "M11.8 4.5H5.2c-1 0-1.7.7-1.7 1.7v12.6c0 1 .7 1.7 1.7 1.7h12.6c1 0 1.7-.7 1.7-1.7v-4.5" +
        "M4.5 17.5l4.2-4.9 4.3 4 2.3-2.6 3.3 3.5",
)
private val PhotoSunPath = addPathNodes("M8.9 8.8a1.2 1.2 0 1 1-2.4 0a1.2 1.2 0 1 1 2.4 0")
private val BookmarkPath = addPathNodes("M14.5 3.5h6v8l-3-2-3 2Z")
private val TrashPath = addPathNodes(
    "M13.5 4.7h8M16.1 4.7V3.6c0-.3.2-.5.5-.5h1.8c.3 0 .5.2.5.5v1.1" +
        "M14.7 4.7l.4 6.4c0 .6.4 1 1 1h2.8c.6 0 1-.4 1-1l.4-6.4M16.7 7v2.6M18.3 7v2.6",
)

@Composable
fun ImportModeIcon(
    mode: ImportMode,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    val badgeColor = if (mode == ImportMode.DeleteBatch && enabled) MaterialTheme.colorScheme.error else tint
    val image = remember(mode, tint, badgeColor) {
        ImageVector.Builder(
            name = "ImportMode${mode.name}",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            addPath(PhotoPath, stroke = SolidColor(tint), strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            addPath(PhotoSunPath, fill = SolidColor(tint))
            addPath(
                if (mode == ImportMode.AddOnly) BookmarkPath else TrashPath,
                stroke = SolidColor(badgeColor),
                strokeLineWidth = if (mode == ImportMode.AddOnly) 1.75f else 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
    Icon(image, contentDescription = null, modifier = modifier.size(24.dp), tint = Color.Unspecified)
}
