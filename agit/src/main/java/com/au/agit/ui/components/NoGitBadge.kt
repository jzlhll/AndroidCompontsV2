package com.au.agit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.au.agit.R
import com.au.agit.ui.theme.AGitTheme
import com.au.agit.ui.theme.LocalThemePalette

@Composable
fun NoGitBadge(modifier: Modifier = Modifier) {
    val colors = LocalThemePalette.current.noGit
    Surface(modifier, shape = RoundedCornerShape(6.dp), color = colors.background,
        contentColor = colors.foreground, border = BorderStroke(1.dp, colors.foreground.copy(alpha = 0.3f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(painterResource(R.drawable.ic_no_git), null, Modifier.size(14.dp))
            Text(stringResource(R.string.no_git), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun NoGitBadgePreview() {
    AGitTheme { NoGitBadge() }
}
