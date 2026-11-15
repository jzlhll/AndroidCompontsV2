package com.allan.picnote.ui

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.data.entity.NodeImageEntity
import com.allan.picnote.data.markdown
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.logEx
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonReducer
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessorRelativeToAbsolute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.commonmark.node.Image
import org.commonmark.node.Paragraph
import org.commonmark.node.BlockQuote
import java.io.File

@Composable
fun LocalImage(model: Any?, description: String, modifier: Modifier = Modifier, crop: Boolean = false, cacheKey: String? = null, onSize: ((Int, Int) -> Unit)? = null) {
    val context = LocalContext.current
    val request = remember(context, model, cacheKey) {
        if (cacheKey == null) model else ImageRequest.Builder(context).data(model).memoryCacheKey(cacheKey).build()
    }
    SubcomposeAsyncImage(
        model = request, contentDescription = description, modifier = modifier,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("图片不可读，请替换图片", style = MaterialTheme.typography.bodySmall) } },
        onSuccess = { result -> onSize?.invoke(result.result.image.width, result.result.image.height) },
    )
}

private sealed interface DocumentBlock {
    data class FormattedText(val text: Spanned) : DocumentBlock
    data class Picture(val destination: String, val description: String, val image: NodeImageEntity?) : DocumentBlock
}

private data class ParsedDocument(val blocks: List<DocumentBlock>, val error: String? = null)

@Composable
fun DocumentPreview(content: DocumentContent, repository: DocumentRepository, scroll: LazyListState, modifier: Modifier = Modifier, active: Boolean = true) {
    val context = LocalContext.current
    val base = remember(content.document.id, repository) { Uri.fromFile(File(repository.documentDirectory(content.document.id), "doc.md")).toString() }
    val destinations = remember(base) { ImageDestinationProcessorRelativeToAbsolute.create(base) }
    val scheme = MaterialTheme.colorScheme
    val textColor = scheme.onSurface.toArgb()
    val linkColor = scheme.primary.toArgb()
    val codeBackground = scheme.surfaceVariant.toArgb()
    val quoteTextColor = scheme.onSurfaceVariant.toArgb()
    val quoteMargin = with(LocalDensity.current) { 24.dp.roundToPx() }
    val ruleColor = scheme.outlineVariant.toArgb()
    val markwon = remember(context, destinations, textColor, linkColor, codeBackground, quoteTextColor, quoteMargin, ruleColor) {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create { plugin -> plugin.errorHandler { _, _ -> ColorDrawable(codeBackground) } })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) { builder.imageDestinationProcessor(destinations) }
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkColor).listItemColor(textColor)
                        .codeTextColor(textColor).codeBlockTextColor(textColor)
                        .codeBackgroundColor(codeBackground).codeBlockBackgroundColor(codeBackground)
                        .headingBreakColor(ruleColor).thematicBreakColor(ruleColor)
                }
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    // 引用统一固定缩进并设为灰字，覆盖代码和链接的颜色，不叠加外层位移。
                    builder.setFactory(BlockQuote::class.java) { _, _ ->
                        arrayOf(LeadingMarginSpan.Standard(quoteMargin), ForegroundColorSpan(quoteTextColor))
                    }
                }
            }).build()
    }
    val parsed by produceState<ParsedDocument?>(null, content, markwon) {
        value = null
        value = try {
            withContext(Dispatchers.Default) {
                val document = markwon.parse(content.markdown())
                val images = content.nodes.flatMap { it.images }.associateBy { destinations.process("media/${it.fileName}") }
                val blocks = MarkwonReducer.directChildren().reduce(document).map { node ->
                    currentCoroutineContext().ensureActive()
                    val child = node.firstChild
                    if (node is Paragraph && child is Image && child.next == null) {
                        val destination = destinations.process(child.destination)
                        DocumentBlock.Picture(destination, markdownPlainText(child).ifBlank { "图片" }, images[destination])
                    } else DocumentBlock.FormattedText(markwon.render(node))
                }
                ParsedDocument(blocks)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            logEx(throwable = e, message = "Parse document Markdown failed")
            ParsedDocument(emptyList(), "文档预览失败，请重新打开文档")
        }
    }
    val document = parsed
    if (document == null) Box(modifier.fillMaxSize())
    else if (document.error != null) Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(document.error) }
    else LazyColumn(modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        itemsIndexed(document.blocks, key = { index, _ -> index }) { _, block ->
            if (block is DocumentBlock.FormattedText) MarkdownBlockText(block.text, markwon, active)
            else if (block is DocumentBlock.Picture) {
                var ratio by rememberSaveable(block.destination) { mutableStateOf(block.image?.let { it.width.toFloat() / it.height } ?: 1f) }
                LocalImage(Uri.parse(block.destination), block.description, Modifier.fillMaxWidth().aspectRatio(ratio),
                    onSize = { width, height -> if (width > 0 && height > 0) ratio = width.toFloat() / height })
            }
        }
    }
}

@Composable
private fun MarkdownBlockText(text: Spanned, markwon: Markwon, active: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f).toArgb()
    AndroidView(
        factory = { TextView(it).apply { textSize = 16f; setTextIsSelectable(true); setTextColor(color) } },
        modifier = modifier.fillMaxWidth(),
        update = { view ->
            view.importantForAccessibility = if (active) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            view.isEnabled = active
            view.isFocusable = active
            view.isFocusableInTouchMode = active
            view.setTextColor(color)
            view.setLinkTextColor(linkColor)
            view.highlightColor = highlightColor
            if (view.tag !== text) { markwon.setParsedMarkdown(view, text); view.tag = text }
        },
    )
}
