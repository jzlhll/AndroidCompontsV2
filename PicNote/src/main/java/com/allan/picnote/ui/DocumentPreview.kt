package com.allan.picnote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
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
import android.text.Layout
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.util.TypedValue
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.allan.picnote.data.entity.DocumentContent
import com.allan.picnote.data.entity.NodeImageEntity
import com.allan.picnote.data.markdownNodes
import com.allan.picnote.data.repositories.DocumentRepository
import com.allan.picnote.logEx
import com.allan.picnote.ui.theme.LocalThemePalette
import com.au.module_androiduiex.styles.ComposeTypography
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonReducer
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.image.DefaultMediaDecoder
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.MediaDecoder
import io.noties.markwon.image.destination.ImageDestinationProcessorRelativeToAbsolute
import io.noties.markwon.image.file.FileSchemeHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.commonmark.node.Image
import org.commonmark.node.Paragraph
import org.commonmark.node.BlockQuote
import org.commonmark.node.Heading
import org.commonmark.node.Block
import org.commonmark.node.CustomBlock
import org.commonmark.parser.Parser
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.ceil

@Composable
fun LocalImage(model: Any?, description: String, modifier: Modifier = Modifier, crop: Boolean = false, cacheKey: String? = null, onSize: ((Int, Int) -> Unit)? = null) {
    val request = if (cacheKey == null) model else {
        val context = LocalContext.current
        remember(context, model, cacheKey) { ImageRequest.Builder(context).data(model).memoryCacheKey(cacheKey).build() }
    }
    var failed by remember(model, cacheKey) { mutableStateOf(false) }
    Box(modifier) {
        AsyncImage(
            model = request, contentDescription = description, modifier = Modifier.fillMaxSize(),
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            onLoading = { failed = false },
            onError = { failed = true },
            onSuccess = { result ->
                failed = false
                onSize?.invoke(result.result.image.width, result.result.image.height)
            },
        )
        if (failed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("图片不可读，请替换图片", style = ComposeTypography.Font12sp)
        }
    }
}

private sealed interface DocumentBlock {
    val key: String
    val nodeId: String

    data class FormattedText(
        override val key: String,
        override val nodeId: String,
        val text: Spanned,
        val quoted: Boolean,
        val heading: Boolean,
        val textSizeSp: Int,
    ) : DocumentBlock

    data class Picture(
        override val key: String,
        override val nodeId: String,
        val destination: String,
        val description: String,
        val image: NodeImageEntity?,
    ) : DocumentBlock
}

private data class ParsedDocument(val blocks: List<DocumentBlock>, val error: String? = null)

@Composable
fun DocumentPreview(content: DocumentContent, repository: DocumentRepository, scroll: LazyListState, onMessage: (String) -> Unit, modifier: Modifier = Modifier, active: Boolean = true, bottomContentPadding: Dp = 24.dp) {
    val context = LocalContext.current
    val showMessage by rememberUpdatedState(onMessage)
    val base = remember(content.document.id, repository) { Uri.fromFile(File(repository.documentDirectory(content.document.id), "doc.md")).toString() }
    val nodeMarker = remember(base) { ":::picnote-${UUID.randomUUID()}:" }
    val destinations = remember(base) { ImageDestinationProcessorRelativeToAbsolute.create(base) }
    val scheme = MaterialTheme.colorScheme
    val textColor = scheme.onSurface.toArgb()
    val linkColor = scheme.primary.toArgb()
    val codeBackground = scheme.surfaceVariant.toArgb()
    val quoteColors = LocalThemePalette.current.introductionContent
    val quoteTextColor = quoteColors.foreground.toArgb()
    val density = LocalDensity.current
    val quoteMargin = with(density) { 24.dp.roundToPx() }
    val titleTextSize = with(density) { 24.sp.toPx() }
    val chapterTextSize = with(density) { 20.sp.toPx() }
    val errorTextSize = with(density) { 14.sp.toPx() }
    val errorPadding = with(density) { 8.dp.toPx() }
    val imageCornerRadius = with(density) { 8.dp.toPx() }
    val ruleColor = scheme.outlineVariant.toArgb()
    val markwon = remember(context, destinations, nodeMarker, density, textColor, linkColor, codeBackground, quoteTextColor, quoteMargin, titleTextSize, chapterTextSize, errorTextSize, errorPadding, imageCornerRadius, ruleColor) {
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
                    .errorHandler { _, _ -> DocumentImageErrorDrawable(codeBackground, textColor, errorTextSize, errorPadding) }
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureParser(builder: Parser.Builder) {
                    builder.customBlockParserFactory(DocumentNodeBlockParser.Factory(nodeMarker))
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.imageDestinationProcessor(destinations)
                        .linkResolver { view, link ->
                            if (Uri.parse(link).scheme.equals("file", ignoreCase = true)) {
                                showMessage("暂不支持直接打开本地文件链接")
                            } else try { linkResolver.resolve(view, link) }
                            catch (e: SecurityException) {
                                logEx(throwable = e, message = "Open Markdown link failed")
                                showMessage("无法打开链接，请检查链接或系统应用")
                            }
                        }
                }
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkColor).listItemColor(textColor)
                        .codeTextColor(textColor).codeBlockTextColor(textColor)
                        .codeBackgroundColor(codeBackground).codeBlockBackgroundColor(codeBackground)
                        .headingBreakColor(ruleColor).thematicBreakColor(ruleColor)
                }
                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(BlockQuote::class.java) { _, _ -> ForegroundColorSpan(quoteTextColor) }
                    builder.setFactory(Heading::class.java) { configuration, props ->
                        val level = CoreProps.HEADING_LEVEL.require(props)
                        when (level) {
                            1 -> DocumentHeadingSpan(configuration.theme(), level, titleTextSize)
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
                        // 顶层引用的缩进由背景内边距提供，嵌套引用保留额外层级。
                        if (quote.parent != null) visitor.setSpans(start, LeadingMarginSpan.Standard(quoteMargin))
                        visitor.setSpansForNodeOptional(quote, start)
                        visitor.blockEnd(quote)
                    }
                }
            }).build()
    }
    val parsed by produceState<ParsedDocument?>(null, content, markwon, nodeMarker) {
        value = null
        value = try {
            withContext(Dispatchers.Default) {
                val sections = content.markdownNodes()
                val source = buildString {
                    sections.forEachIndexed { index, section ->
                        append(nodeMarker).append(index).append("\n\n").append(section.source)
                    }
                }
                val document = markwon.parse(source)
                val images = content.nodes.flatMap { it.images }.associateBy { destinations.process("media/${it.fileName}") }
                val reducer = MarkwonReducer.directChildren()
                val blocks = buildList {
                    reducer.reduce(document).forEach { container ->
                        currentCoroutineContext().ensureActive()
                        check(container is DocumentNodeBlock) { "Document node boundary was lost" }
                        val section = sections[container.index]
                        val textSizeSp = if (section.kind == "body" && !section.hasImages) 18 else 16
                        reducer.reduce(container).forEachIndexed { index, node ->
                            currentCoroutineContext().ensureActive()
                            val key = "${section.nodeId}:$index"
                            val child = node.firstChild
                            if (node is Paragraph && child is Image && child.next == null) {
                                val destination = destinations.process(child.destination)
                                add(DocumentBlock.Picture(key, section.nodeId, destination,
                                    markdownPlainText(child).ifBlank { "图片" }, images[destination]))
                            } else add(DocumentBlock.FormattedText(key, section.nodeId, markwon.render(node),
                                quoted = node is BlockQuote, heading = node is Heading, textSizeSp = textSizeSp))
                        }
                    }
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
    else LazyColumn(modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = bottomContentPadding)) {
        itemsIndexed(document.blocks, key = { _, block -> block.key }, contentType = { _, block ->
            when (block) {
                is DocumentBlock.Picture -> "picture"
                is DocumentBlock.FormattedText -> if (block.quoted) "quote" else "text"
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
            if (block is DocumentBlock.FormattedText) {
                val textModifier = if (block.quoted) blockModifier.clip(RoundedCornerShape(8.dp))
                    .background(quoteColors.background).padding(horizontal = 24.dp, vertical = 8.dp) else blockModifier
                MarkdownBlockText(block.text, markwon, active, with(density) { block.textSizeSp.sp.toPx() }, textModifier)
            }
            else if (block is DocumentBlock.Picture) {
                var ratio by rememberSaveable(block.destination) { mutableStateOf(block.image?.let { it.width.toFloat() / it.height } ?: 1f) }
                LocalImage(Uri.parse(block.destination), block.description, blockModifier.aspectRatio(ratio).clip(RoundedCornerShape(8.dp)),
                    onSize = { width, height -> if (width > 0 && height > 0) ratio = width.toFloat() / height })
            }
        }
    }
}

@Composable
private fun MarkdownBlockText(text: Spanned, markwon: Markwon, active: Boolean, textSizePx: Float, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
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
            if (view.currentTextColor != color) view.setTextColor(color)
            if (view.linkTextColors.defaultColor != linkColor) view.setLinkTextColor(linkColor)
            if (view.highlightColor != highlightColor) view.highlightColor = highlightColor
            if (view.tag !== text) { markwon.setParsedMarkdown(view, text); view.tag = text }
        },
    )
}

/** 在一次 Markdown 解析中保留节点边界，引用定义仍由整篇文档共享。 */
private class DocumentNodeBlock(val index: Int) : CustomBlock()

/** 以阅读专用标记分隔节点容器，未闭合的代码或引用不会跨入下一个节点。 */
private class DocumentNodeBlockParser(private val node: DocumentNodeBlock, private val marker: String) : AbstractBlockParser() {
    override fun getBlock(): Block = node
    override fun isContainer(): Boolean = true
    override fun canContain(childBlock: Block): Boolean = childBlock !is DocumentNodeBlock

    override fun tryContinue(state: ParserState): BlockContinue? =
        if (state.line.toString().startsWith(marker)) BlockContinue.none() else BlockContinue.atIndex(state.index)

    class Factory(private val marker: String) : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            val line = state.line.toString()
            if (state.index != 0 || !line.startsWith(marker)) return BlockStart.none()
            val index = line.substring(marker.length).toIntOrNull() ?: return BlockStart.none()
            return BlockStart.of(DocumentNodeBlockParser(DocumentNodeBlock(index), marker)).atIndex(line.length)
        }
    }
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
private class DocumentHeadingSpan(theme: MarkwonTheme, private val headingLevel: Int, private val textSize: Float) : HeadingSpan(theme, headingLevel) {
    private val headingTypeface = Typeface.create(Typeface.DEFAULT, if (headingLevel == 1) 600 else 500, false)

    override fun updateMeasureState(paint: TextPaint) {
        paint.textSize = textSize
        paint.typeface = headingTypeface
        paint.isFakeBoldText = false
    }

    override fun updateDrawState(paint: TextPaint) = updateMeasureState(paint)

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
private class DocumentImageErrorDrawable(background: Int, foreground: Int, labelSize: Float, private val padding: Float) : Drawable() {
    private val label = "图片不可读，请替换图片"
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
