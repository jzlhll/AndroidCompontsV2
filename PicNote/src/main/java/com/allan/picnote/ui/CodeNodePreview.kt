package com.allan.picnote.ui

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.allan.picnote.R
import com.allan.picnote.data.CodeLanguages
import com.allan.picnote.data.parseCodeBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val CodePreviewCacheCharacters = 4 * 1024 * 1024
private const val CodePreviewCacheEntries = 16
private const val CodePreviewPlaceholderLines = 14

data class CodePreviewContent(val code: String, val language: String? = null, val description: String = "")

/** 缓存当前文档的长代码解析，避免卡片从 LazyColumn 移除后重复加载。 */
class CodePreviewCache {
    private data class Entry(val markdown: String, val content: CodePreviewContent) {
        val characters = markdown.length.toLong() + (if (content.code === markdown) 0 else content.code.length) +
            content.description.length + content.language.orEmpty().length
    }

    private val entries = object : LruCache<String, Entry>(CodePreviewCacheCharacters) {
        override fun sizeOf(key: String, value: Entry): Int {
            // 每项计入最低占用，同时限制缓存总量和项数。
            return max(CodePreviewCacheCharacters / CodePreviewCacheEntries, value.characters.toInt())
        }
    }

    fun get(nodeId: String, markdown: String): CodePreviewContent? =
        entries.get(nodeId)?.takeIf { it.markdown === markdown }?.content

    fun put(nodeId: String, markdown: String, content: CodePreviewContent) {
        val entry = Entry(markdown, content)
        if (entry.characters <= CodePreviewCacheCharacters) entries.put(nodeId, entry)
    }
}

private class CodePreviewSource(private val markdown: String) {
    override fun equals(other: Any?): Boolean = other is CodePreviewSource && markdown === other.markdown
    override fun hashCode(): Int = System.identityHashCode(markdown)
}

private fun parseCodePreview(markdown: String): CodePreviewContent {
    val block = parseCodeBlock(markdown) ?: return CodePreviewContent(code = markdown)
    return CodePreviewContent(block.code, block.language, block.description)
}

private fun estimatePreviewLines(markdown: String): Int {
    var breaks = 0
    for (index in 0 until min(markdown.length, CodeHighlightRegionSize)) {
        val char = markdown[index]
        if (char == '\n' || (char == '\r' && markdown.getOrNull(index + 1) != '\n')) {
            if (++breaks == CodePreviewPlaceholderLines) break
        }
    }
    // 围栏也占一行，超长单行不用按整张卡片的高度预留。
    return max(1, breaks - 1)
}

@Composable
fun CodeNodePreview(
    nodeId: String,
    markdown: String,
    enabled: Boolean,
    wrapCode: Boolean,
    cache: CodePreviewCache,
    modifier: Modifier = Modifier,
) {
    val source = CodePreviewSource(markdown)
    val parsed = if (markdown.length <= CodeHighlightRegionSize) {
        remember(source) { parseCodePreview(markdown) }
    } else key(nodeId, source, cache) {
        val initial = remember { cache.get(nodeId, markdown) }
        val result by produceState(initial) {
            if (initial == null) {
                val loaded = withContext(Dispatchers.Default) { parseCodePreview(markdown) }
                cache.put(nodeId, markdown, loaded)
                value = loaded
            }
        }
        result
    }
    val verticalState = rememberScrollState()
    val horizontalState = rememberScrollState()
    val label = when {
        parsed == null -> stringResource(R.string.code_preview_loading)
        parsed.language == null -> stringResource(R.string.code_markdown_source)
        else -> codeLanguageLabel(parsed.language)
    }
    val codeSurface = Modifier.fillMaxWidth().clip(RoundedCornerShape(CodeCornerRadius))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    val codeStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = CodeTextSize)
    Column(modifier.padding(CodeContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (parsed == null) {
            val estimate = remember(source) { estimatePreviewLines(markdown) }
            val lineHeight = with(LocalDensity.current) {
                (if (codeStyle.lineHeight.isSpecified) codeStyle.lineHeight else CodeTextSize).toDp()
            }
            Box(codeSurface.height(lineHeight * estimate + CodeContentPadding * 2), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            return@Column
        }
        if (parsed.description.isNotBlank()) {
            Text(
                text = parsed.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val highlighted = rememberHighlightedCode(parsed.code, parsed.language.orEmpty())
        Box(Modifier.weight(1f, fill = false).then(codeSurface)) {
            val horizontalScroll = if (wrapCode) Modifier else Modifier.horizontalScroll(horizontalState, enabled = enabled)
            Text(
                text = highlighted,
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(verticalState, enabled = enabled)
                    .then(horizontalScroll)
                    .padding(CodeContentPadding),
                style = codeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = wrapCode,
            )
        }
    }
}

@Composable
fun codeLanguageLabel(language: String): String = CodeLanguages[language.lowercase()]
    ?: language.ifBlank { stringResource(R.string.plain_text) }
