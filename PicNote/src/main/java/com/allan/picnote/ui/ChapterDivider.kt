package com.allan.picnote.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.allan.picnote.R
import com.allan.picnote.ui.theme.LocalThemePalette
import com.au.module_androiduiex.styles.ComposeTypography

@Composable
fun ChapterDivider(number: String, modifier: Modifier = Modifier) {
    SectionDivider(stringResource(R.string.chapter_number, number), modifier)
}

@Composable
fun SectionDivider(label: String, modifier: Modifier = Modifier) {
    val color = LocalThemePalette.current.introductionContent.foreground
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), thickness = 0.5.dp, color = color)
        Text(label, Modifier.padding(horizontal = 12.dp), style = ComposeTypography.Font13sp,
            color = color, maxLines = 1, softWrap = false)
        HorizontalDivider(Modifier.weight(1f), thickness = 0.5.dp, color = color)
    }
}
