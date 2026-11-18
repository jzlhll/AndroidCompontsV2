package com.allan.picnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allan.picnote.R
import com.allan.picnote.data.CodeBlockContent
import com.allan.picnote.data.CodeLanguages
import com.allan.picnote.data.parseCodeBlock

@Composable
fun CodeNodePreview(
    markdown: String,
    enabled: Boolean,
    wrapCode: Boolean,
    modifier: Modifier = Modifier,
) {
    val block = remember(markdown) { parseCodeBlock(markdown) ?: CodeBlockContent(code = markdown) }
    val highlighted = rememberHighlightedCode(block.code, block.language)
    val verticalState = rememberScrollState()
    val language = CodeLanguages[block.language] ?: block.language.ifBlank { stringResource(R.string.plain_text) }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = language,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (block.description.isNotBlank()) {
            Text(
                text = block.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            val horizontalScroll = if (wrapCode) Modifier else Modifier.horizontalScroll(rememberScrollState(), enabled = enabled)
            Text(
                text = highlighted,
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(verticalState, enabled = enabled)
                    .then(horizontalScroll)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = wrapCode,
            )
        }
    }
}
