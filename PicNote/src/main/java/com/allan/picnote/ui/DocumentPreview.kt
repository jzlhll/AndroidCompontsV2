package com.allan.picnote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.net.Uri
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.Layout
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.AlignmentSpan
import android.text.style.LineHeightSpan
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.util.TypedValue
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import com.allan.picnote.media.ScrollThumbnail
import com.allan.picnote.media.scrollThumbnailCacheKey
import com.allan.picnote.R
import com.allan.picnote.data.CodeLanguageToken
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.data.entity.NodeImageEntity
import com.allan.picnote.data.entity.chapterNumbers
import com.allan.picnote.data.MarkdownBlockSeparator
import com.allan.picnote.data.MarkdownReference
import com.allan.picnote.data.NodeMarkdown
import com.allan.picnote.data.markdownNodes
import com.allan.picnote.data.parseMarkdownSections
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.logEx
import com.allan.picnote.ui.theme.LocalThemePalette
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.styles.onClick
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.utils.LeadingMarginUtils
import io.noties.markwon.image.DefaultMediaDecoder
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.MediaDecoder
import io.noties.markwon.image.destination.ImageDestinationProcessorRelativeToAbsolute
import io.noties.markwon.image.file.FileSchemeHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.node.Image
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Paragraph
import org.commonmark.node.BlockQuote
import org.commonmark.node.Heading
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LocalImage(model: Any?, description: String, modifier: Modifier = Modifier, crop: Boolean = false, cacheKey: String? = null, onSize: ((Int, Int) -> Unit)? = null, onResult: (() -> Unit)? = null) {
    val context = LocalContext.current
    val imageLoader = SingletonImageLoader.get(context)
    val file = remember(model) {
        when (model) {
            is File -> model
            is Uri -> if (model.scheme == "file") model.path?.let { File(it) } else null
            else -> null
        }
    }
    val baseKey = if (file != null) "${file.absolutePath}:${cacheKey.orEmpty()}" else cacheKey ?: model.toString()
    val fullKey = "$baseKey:full"
    val thumbnailKey = scrollThumbnailCacheKey(file?.absolutePath ?: baseKey)
    val request = remember(context, model, fullKey) {
        ImageRequest.Builder(context)
            .data(model)
            .memoryCacheKey(fullKey)
            .placeholderMemoryCacheKey(fullKey)
            .build()
    }
    val thumbnailRequest = remember(context, file, thumbnailKey) {
        file?.let {
            ImageRequest.Builder(context)
                .data(ScrollThumbnail(it, thumbnailKey, cacheOnly = true))
                .memoryCacheKey(thumbnailKey)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .allowRgb565(true)
                .allowHardware(false)
                .build()
        }
    }
    var fullReady by remember(model, cacheKey) { mutableStateOf(false) }
    var prepareThumbnail by remember(model, cacheKey) { mutableStateOf(false) }
    var failed by remember(model, cacheKey) { mutableStateOf(false) }
    if (file != null && thumbnailRequest != null && prepareThumbnail) {
        LaunchedEffect(imageLoader, file, thumbnailRequest, thumbnailKey) {
            imageLoader.execute(thumbnailRequest.newBuilder().data(ScrollThumbnail(file, thumbnailKey)).build())
        }
    }
    Box(modifier) {
        // 主图请求始终继续，缓存小图只在主图完成前占位，不能覆盖已显示的清晰图。
        if (!fullReady && thumbnailRequest != null) AsyncImage(
            model = thumbnailRequest, imageLoader = imageLoader, contentDescription = null, modifier = Modifier.fillMaxSize(),
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        )
        AsyncImage(
            model = request, imageLoader = imageLoader, contentDescription = description, modifier = Modifier.fillMaxSize(),
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            onLoading = { failed = false },
            onError = { failed = true; onResult?.invoke() },
            onSuccess = { result ->
                failed = false
                fullReady = true
                if (result.result.dataSource != DataSource.MEMORY_CACHE) prepareThumbnail = true
                onSize?.invoke(result.result.image.width, result.result.image.height)
                onResult?.invoke()
            },
        )
        if (failed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.image_unreadable_replace), style = ComposeTypography.Font12sp)
        }
    }
}

private sealed interface DocumentBlock {
    val key: String
    val nodeId: String

    data class ChapterLabel(
        override val key: String,
        override val nodeId: String,
        val number: String,
    ) : DocumentBlock

    data class FormattedText(
        override val key: String,
        override val nodeId: String,
        val text: Spanned,
        val quoted: Boolean,
        val introduction: Boolean,
        val heading: Boolean,
        val codeLanguage: String?,
        val textSizeSp: Int,
    ) : DocumentBlock

    data class SectionWarning(
        override val key: String,
        override val nodeId: String,
    ) : DocumentBlock

    data class Picture(
        override val key: String,
        override val nodeId: String,
        val destination: String,
        val description: String,
    ) : DocumentBlock
}

private data class RenderedSection(
    val markdown: NodeMarkdown,
    val blocks: List<DocumentBlock>,
    val usesRawMarkdown: Boolean = false,
)

private class ParsedDocument(
    val markwon: Markwon,
    val sections: List<RenderedSection>,
    val references: List<MarkdownReference>,
    chapterNumbers: Map<String, String>,
) {
    val blocks = buildList {
        sections.forEach { section ->
            val nodeId = section.markdown.nodeId
            chapterNumbers[nodeId]?.let { number ->
                add(DocumentBlock.ChapterLabel("$nodeId:chapter-divider", nodeId, number))
            }
            addAll(section.blocks)
        }
    }
}

@Composable
fun DocumentPreview(
    content: DocumentContent,
    repository: DocumentRepository,
    scroll: LazyListState,
    onMessage: (String) -> Unit,
    onDownloadImage: (String) -> Unit,
    wrapCode: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    enabled: Boolean = true,
    bottomContentPadding: Dp = 24.dp,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val showMessage by rememberUpdatedState(onMessage)
    val base = remember(content.document.id, repository) { Uri.fromFile(File(repository.documentDirectory(content.document.id), "doc.md")).toString() }
    val destinations = remember(base) { ImageDestinationProcessorRelativeToAbsolute.create(base) }
    val scheme = MaterialTheme.colorScheme
    val textColor = scheme.onSurface.toArgb()
    val linkColor = scheme.primary.toArgb()
    val codeBackground = scheme.surfaceVariant.toArgb()
    val codeTextColor = scheme.onSurfaceVariant.toArgb()
    val quoteColors = LocalThemePalette.current.introductionContent
    val quoteTextColor = quoteColors.foreground.toArgb()
    val density = LocalDensity.current
    val quoteMargin = with(density) { 24.dp.roundToPx() }
    val titleTextSize = with(density) { 24.sp.toPx() }
    val titleUnderlineGap = with(density) { 2.dp.roundToPx() }
    val chapterTextSize = with(density) { 20.sp.toPx() }
    val errorTextSize = with(density) { 14.sp.toPx() }
    val errorPadding = with(density) { 8.dp.toPx() }
    val imageCornerRadius = with(density) { 8.dp.toPx() }
    val codeTextSize = with(density) { CodeTextSize.roundToPx() }
    val ruleColor = scheme.outlineVariant.toArgb()
    val codeHighlight = rememberCodeSyntaxHighlight()
    val imageError = stringResource(R.string.image_unreadable_replace)
    val localLinkError = stringResource(R.string.local_link_unsupported)
    val openLinkError = stringResource(R.string.open_link_failed)
    val markwon = remember(context, destinations, density, scheme, quoteTextColor,
        codeHighlight, imageError, localLinkError, openLinkError) {
        val linkResolver = LinkResolverDef()
        val mediaDecoder = DefaultMediaDecoder.create(context.resources)
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create { plugin ->
                plugin.addSchemeHandler(FileSchemeHandler.create())
                    .defaultMediaDecoder(object : MediaDecoder() {
                        override fun decode(contentType: String?, inputStream: InputStream): Drawable = mediaDecoder.decode(contentType, inputStream).also {
                            require(it.intrinsicWidth > 0 && it.intrinsicHeight > 0) { "Image data could not be decoded" }
                        }.let { RoundedDocumentImageDrawable(it, imageCornerRadius) }
                        override fun supportedTypes(): Collection<String> = mediaDecoder.supportedTypes()
                    })
                    .errorHandler { _, _ -> DocumentImageErrorDrawable(imageError, codeBackground, textColor, errorTextSize, errorPadding) }
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.syntaxHighlight(codeHighlight).imageDestinationProcessor(destinations)
                        .linkResolver { view, link ->
                            if (Uri.parse(link).scheme.equals("file", ignoreCase = true)) {
                                showMessage(localLinkError)
                            } else try { linkResolver.resolve(view, link) }
                            catch (e: SecurityException) {
                                logEx(throwable = e, message = "Open Markdown link failed")
                                showMessage(openLinkError)
                            }
                        }
                }
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkColor).listItemColor(textColor)
                        .codeTextColor(textColor).codeBlockTextColor(codeTextColor).codeBlockTextSize(codeTextSize)
                        .codeBackgroundColor(codeBackground).codeBlockBackgroundColor(codeBackground)
                        .headingBreakColor(ruleColor).thematicBreakColor(ruleColor)
                }
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(BlockQuote::class.java) { _, _ -> ForegroundColorSpan(quoteTextColor) }
                    builder.setFactory(Heading::class.java) { configuration, props ->
                        val level = CoreProps.HEADING_LEVEL.require(props)
                        when (level) {
                            1 -> DocumentHeadingSpan(configuration.theme(), level, titleTextSize, titleUnderlineGap)
                            2 -> DocumentHeadingSpan(configuration.theme(), level, chapterTextSize)
                            else -> DocumentHeadingSpan(configuration.theme(), level, with(density) { (24 - level * 2).sp.toPx() })
                        }
                    }
                }
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(BlockQuote::class.java) { visitor, quote ->
                        visitor.blockStart(quote)
                        val start = visitor.length()
                        visitor.visitChildren(quote)
                        // 顶层块在独立渲染前已解除父节点，引用嵌在列表中时也保留一层缩进。
                        if (quote.parent != null) visitor.setSpans(start, LeadingMarginSpan.Standard(quoteMargin))
                        visitor.setSpansForNodeOptional(quote, start)
                        visitor.blockEnd(quote)
                    }
                }
            }).build()
    }
    val locales = LocalConfiguration.current.locales
    var parseFailed by remember(base) { mutableStateOf(false) }
    val parsed by key(base) {
        produceState<ParsedDocument?>(null, content, markwon, locales) {
            parseFailed = false
            try {
                val previous = value
                value = withContext(Dispatchers.Default) {
                    val sections = content.markdownNodes(resources)
                    if (previous?.markwon === markwon && previous.sections.none { it.usesRawMarkdown } &&
                        previous.sections.map { it.markdown } == sections
                    ) previous
                    else renderDocument(sections, content.chapterNumbers(), markwon, destinations, codeHighlight, previous)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Parse document Markdown failed")
                parseFailed = true
            }
        }
    }
    val images = remember(content.nodes, destinations) {
        content.nodes.flatMap { it.images }.associateBy { image -> destinations.process("media/${image.fileName}") }
    }
    val document = parsed
    Column(modifier.fillMaxSize()) {
        if (parseFailed) Text(
            stringResource(R.string.document_preview_failed),
            Modifier.fillMaxWidth().padding(24.dp),
            color = MaterialTheme.colorScheme.error,
        )
        if (document != null) LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll,
            contentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = bottomContentPadding)) {
            itemsIndexed(document.blocks, key = { _, block -> block.key }, contentType = { _, block ->
                when (block) {
                    is DocumentBlock.ChapterLabel -> "chapter-divider"
                    is DocumentBlock.SectionWarning -> "section-warning"
                    is DocumentBlock.Picture -> "picture"
                    is DocumentBlock.FormattedText -> when {
                        block.quoted -> "quote"
                        block.codeLanguage != null -> "code"
                        else -> "text"
                    }
                }
            }) { index, block ->
                val previous = document.blocks.getOrNull(index - 1)
                val gap = when {
                    previous == null -> 0.dp
                    previous.nodeId == block.nodeId -> 6.dp
                    else -> 12.dp
                }
                val headingPadding = if (block is DocumentBlock.FormattedText && block.heading) 6.dp else 0.dp
                val blockModifier = Modifier.fillMaxWidth().padding(top = gap + headingPadding, bottom = headingPadding)
                if (block is DocumentBlock.ChapterLabel) {
                    ChapterDivider(block.number, blockModifier)
                } else if (block is DocumentBlock.SectionWarning) {
                    Text(stringResource(R.string.document_section_raw_markdown), blockModifier, color = scheme.error)
                } else if (block is DocumentBlock.FormattedText) {
                    if (block.quoted) {
                        Box(
                            modifier = blockModifier.clip(RoundedCornerShape(8.dp)).background(quoteColors.background)
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .heightIn(min = if (block.introduction) 36.dp else 0.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            MarkdownBlockText(block.text, document.markwon, active, with(density) { block.textSizeSp.sp.toPx() })
                        }
                    } else if (block.codeLanguage != null) {
                        val horizontalScroll = rememberScrollState()
                        Column(blockModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(codeLanguageLabel(block.codeLanguage), style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant, maxLines = 1)
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(CodeCornerRadius)).background(scheme.surfaceVariant)) {
                                val scrolling = if (wrapCode) Modifier else Modifier.horizontalScroll(horizontalScroll, enabled = active)
                                MarkdownBlockText(block.text, document.markwon, active, codeTextSize.toFloat(),
                                    modifier = scrolling.padding(CodeContentPadding), code = true)
                            }
                        }
                    } else {
                        MarkdownBlockText(block.text, document.markwon, active, with(density) { block.textSizeSp.sp.toPx() }, blockModifier)
                    }
                } else if (block is DocumentBlock.Picture) {
                    val image = images[block.destination]
                    val description = block.description.ifBlank { stringResource(R.string.image) }
                    DocumentImage(block, description, image, enabled = active && enabled && image != null,
                        onDownload = { image?.let { onDownloadImage(it.id) } }, modifier = blockModifier)
                }
            }
        }
    }
}

private suspend fun renderDocument(
    sections: List<NodeMarkdown>,
    chapterNumbers: Map<String, String>,
    markwon: Markwon,
    destinations: ImageDestinationProcessorRelativeToAbsolute,
    highlighter: CodeSyntaxHighlight,
    previous: ParsedDocument?,
): ParsedDocument {
    val coroutineContext = currentCoroutineContext()
    val parsed = parseMarkdownSections(sections) { coroutineContext.ensureActive() }
    val references = parsed.references
    // 引用在整篇文档中解析，定义变化时重新生成可能依赖这些定义的样式。
    val cached = if (previous?.markwon === markwon && previous.references == references) {
        previous.sections.associateBy { it.markdown.nodeId }
    } else emptyMap()
    val rendered = sections.mapIndexed { sectionIndex, section ->
        coroutineContext.ensureActive()
        if (sectionIndex in parsed.rawSections) return@mapIndexed renderRawMarkdownSection(section)
        cached[section.nodeId]?.let { if (it.markdown == section && !it.usesRawMarkdown) return@mapIndexed it }
        val container = parsed.documents[sectionIndex]
        val textSize = if (section.kind == "body" && !section.hasImages) 18 else 16
        try {
            val blocks = generateSequence(container.firstChild) { it.next }
                .filterNot { it is LinkReferenceDefinition }
                .toList()
                .mapIndexed { index, node ->
                    coroutineContext.ensureActive()
                    val key = "${section.nodeId}:$index"
                    val child = node.firstChild
                    if (node is Paragraph && child is Image && child.next == null) {
                        DocumentBlock.Picture(key, section.nodeId, destinations.process(child.destination), markdownPlainText(child))
                    } else {
                        val codeLanguage = when (node) {
                            is FencedCodeBlock -> CodeLanguageToken.find(node.info.orEmpty())?.value.orEmpty()
                            is IndentedCodeBlock -> ""
                            else -> null
                        }
                        // 独立显示的块不带相邻兄弟节点，间距由 LazyColumn 统一处理。
                        node.unlink()
                        val renderedText = when (node) {
                            is FencedCodeBlock -> SpannableStringBuilder(highlighter.highlight(node.info, node.literal.removeSuffix("\n")) { coroutineContext.ensureActive() })
                            is IndentedCodeBlock -> SpannableStringBuilder(node.literal.removeSuffix("\n"))
                            else -> markwon.render(node)
                        }
                        val text = if (section.kind == "title") SpannableStringBuilder(renderedText).apply {
                            setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        } else renderedText
                        DocumentBlock.FormattedText(key, section.nodeId, text, quoted = node is BlockQuote,
                            introduction = section.kind == "introduction", heading = node is Heading,
                            codeLanguage = codeLanguage, textSizeSp = textSize)
                    }
                }
            RenderedSection(section, blocks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: StackOverflowError) {
            logEx(throwable = e, message = "Render document section exceeded renderer stack")
            renderRawMarkdownSection(section)
        } catch (e: Exception) {
            logEx(throwable = e, message = "Render document section failed")
            renderRawMarkdownSection(section)
        }
    }
    return ParsedDocument(markwon, rendered, references, chapterNumbers)
}

private fun renderRawMarkdownSection(section: NodeMarkdown) = RenderedSection(
    markdown = section,
    blocks = listOf(
        DocumentBlock.SectionWarning("${section.nodeId}:raw-warning", section.nodeId),
        DocumentBlock.FormattedText(
            key = "${section.nodeId}:raw",
            nodeId = section.nodeId,
            text = SpannableStringBuilder(section.source.removeSuffix(MarkdownBlockSeparator)),
            quoted = false,
            introduction = false,
            heading = false,
            codeLanguage = "",
            textSizeSp = 16,
        ),
    ),
    usesRawMarkdown = true,
)

@Composable
private fun DocumentImage(
    block: DocumentBlock.Picture,
    description: String,
    image: NodeImageEntity?,
    enabled: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val storedRatio = image?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
    var decodedRatio by rememberSaveable(block.destination) { mutableFloatStateOf(1f) }
    val ratio = storedRatio ?: decodedRatio
    val controlsAlpha = remember(block.destination) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val controlsJob = remember(block.destination) { ImageControlsJob() }
    val fadingVisible by remember(controlsAlpha) { derivedStateOf { controlsAlpha.value > 0f } }
    val downloadDescription = stringResource(R.string.download_image)
    LaunchedEffect(enabled, controlsAlpha) {
        if (!enabled) {
            controlsJob.job?.cancel()
            controlsAlpha.snapTo(0f)
        }
    }
    DisposableEffect(controlsAlpha) { onDispose { controlsJob.job?.cancel() } }
    Box(modifier.aspectRatio(ratio).clip(RoundedCornerShape(8.dp))
        .onClick(enabled = enabled, onClickLabel = stringResource(R.string.show_image_actions)) {
            controlsJob.job?.cancel()
            controlsJob.job = scope.launch {
                controlsAlpha.animateTo(1f, tween(500))
                delay(3000.milliseconds)
                controlsAlpha.animateTo(0f, tween(500))
            }
        }.semantics {
            customActions = if (enabled) listOf(CustomAccessibilityAction(downloadDescription) { onDownload(); true }) else emptyList()
        }) {
        LocalImage(Uri.parse(block.destination), description, Modifier.fillMaxSize(),
            onSize = { width, height ->
                if (storedRatio == null && width > 0 && height > 0) {
                    val decoded = width.toFloat() / height
                    if (abs(decoded / decodedRatio - 1f) > 0.01f) decodedRatio = decoded
                }
            })
        if (fadingVisible && enabled) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = controlsAlpha.value }
                .background(Color.Black.copy(alpha = 0.2f)))
            IconButton(onClick = onDownload,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(48.dp)
                    .graphicsLayer { alpha = controlsAlpha.value },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = downloadDescription, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private class ImageControlsJob(var job: Job? = null)

@Composable
private fun MarkdownBlockText(
    text: Spanned,
    markwon: Markwon,
    active: Boolean,
    textSizePx: Float,
    modifier: Modifier = Modifier,
    code: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val color = (if (code) scheme.onSurfaceVariant else scheme.onSurface).toArgb()
    val typeface = if (code) Typeface.MONOSPACE else Typeface.DEFAULT
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f).toArgb()
    AndroidView(
        factory = { DocumentTextView(it).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            includeFontPadding = false
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
            setTextColor(color)
            this.typeface = typeface
        } },
        modifier = modifier.fillMaxWidth(),
        onReset = { view ->
            AsyncDrawableScheduler.unschedule(view)
            view.clearFocus()
            view.text = null
            view.tag = null
        },
        onRelease = { view -> AsyncDrawableScheduler.unschedule(view) },
        update = { view ->
            view.importantForAccessibility = if (active) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            view.isEnabled = active
            view.isFocusable = active
            view.isFocusableInTouchMode = active
            if (view.textSize != textSizePx) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            if (view.typeface != typeface) view.typeface = typeface
            if (view.currentTextColor != color) view.setTextColor(color)
            if (view.linkTextColors.defaultColor != linkColor) view.setLinkTextColor(linkColor)
            if (view.highlightColor != highlightColor) view.highlightColor = highlightColor
            if (view.tag !== text) { markwon.setParsedMarkdown(view, text); view.tag = text }
        },
    )
}

/** 为文字或引用内的混排图片应用与独立图片一致的圆角。 */
private class RoundedDocumentImageDrawable(drawable: Drawable, private val radius: Float) : DrawableWrapper(drawable) {
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        path.rewind()
        path.addRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), radius, radius, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipPath(path)
        super.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 统一标题字号与字重，仅在总标题下绘制分隔线。 */
private class DocumentHeadingSpan(
    theme: MarkwonTheme,
    private val headingLevel: Int,
    private val textSize: Float,
    private val underlineGap: Int = 0,
) : HeadingSpan(theme, headingLevel), LineHeightSpan {
    private val headingTypeface = Typeface.create(Typeface.DEFAULT, if (headingLevel == 1) 600 else 500, false)

    override fun updateMeasureState(paint: TextPaint) {
        paint.textSize = textSize
        paint.typeface = headingTypeface
        paint.isFakeBoldText = false
    }

    override fun updateDrawState(paint: TextPaint) = updateMeasureState(paint)

    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fontMetrics: Paint.FontMetricsInt) {
        if (headingLevel == 1 && LeadingMarginUtils.selfEnd(end, text, this)) {
            // 只增加末行文字下方的空间，下划线随行底部下移，保持文字基线不变。
            fontMetrics.descent += underlineGap
            fontMetrics.bottom += underlineGap
        }
    }

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout,
    ) {
        if (headingLevel == 1) super.drawLeadingMargin(canvas, paint, x, dir, top, baseline, bottom, text, start, end, first, layout)
    }
}

/** 在平台主题下使用系统文字控件，按完整内容测量并由外层 LazyColumn 滚动，保留选择和复制能力。 */
@SuppressLint("AppCompatCustomView")
private class DocumentTextView(context: Context) : TextView(context) {
    override fun scrollTo(x: Int, y: Int) { super.scrollTo(0, 0) }

    // 仅让用户正在操作的非空选区带动外层列表，首次焦点和预绘制不自动追随光标。
    override fun bringPointIntoView(offset: Int): Boolean = if (isFocused && hasSelection()) super.bringPointIntoView(offset) else false
}

/** 为混排图片提供具有有效尺寸的错误占位，避免失败后替代文字被空图覆盖。 */
private class DocumentImageErrorDrawable(
    private val label: String,
    background: Int,
    foreground: Int,
    labelSize: Float,
    private val padding: Float,
) : Drawable() {
    private val backgroundPaint = Paint().apply { color = background }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground; textSize = labelSize }
    private val metrics = textPaint.fontMetrics
    private val drawableWidth = ceil(textPaint.measureText(label) + padding * 2).toInt()
    private val drawableHeight = ceil(metrics.bottom - metrics.top + padding * 2).toInt()

    override fun getIntrinsicWidth(): Int = drawableWidth
    override fun getIntrinsicHeight(): Int = drawableHeight

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width().toFloat() / drawableWidth, bounds.height().toFloat() / drawableHeight)
        canvas.drawRect(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat(), backgroundPaint)
        canvas.drawText(label, padding, padding - metrics.top, textPaint)
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) { backgroundPaint.alpha = alpha; textPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { backgroundPaint.colorFilter = colorFilter; textPaint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
