package com.allan.picnote.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allan.picnote.data.MaxProcessedCodeLength
import com.allan.picnote.data.normalizeCodeLanguage
import com.allan.picnote.logEx
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

const val CodeHighlightRegionSize = 8 * 1024
private val codeHighlightDispatcher = Dispatchers.Default.limitedParallelism(1)
private val codePrism = Prism4j(CodeGrammar())
private val lightCodeHighlight by lazy { CodeSyntaxHighlight(false) }
private val darkCodeHighlight by lazy { CodeSyntaxHighlight(true) }

val CodeTextSize = 14.sp
val CodeContentPadding = 12.dp
val CodeCornerRadius = 8.dp

/** 为编辑器与阅读页提供相同的语法着色，失败时保留完整代码。 */
class CodeSyntaxHighlight(dark: Boolean) : Prism4jSyntaxHighlight(
    codePrism,
    if (dark) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create(),
    null,
) {
    override fun highlight(info: String?, code: String): CharSequence = highlight(info, code) {}

    fun highlight(info: String?, code: String, checkActive: () -> Unit): CharSequence {
        val language = normalizeCodeLanguage(info)
        if (language.isEmpty() || code.isEmpty() || code.length > MaxProcessedCodeLength) return code
        checkActive()
        if (code.length <= CodeHighlightRegionSize) return highlightRegion(language, code)

        // 优先在空行处分区。单行超过上限时整行用纯文本，下一行重新着色。
        val result = SpannableStringBuilder()
        var start = 0
        while (code.length - start > CodeHighlightRegionSize) {
            checkActive()
            val limit = start + CodeHighlightRegionSize
            val lastLineEnd = code.lastIndexOf('\n', limit - 1)
            if (lastLineEnd < start) {
                val nextLineEnd = code.indexOf('\n', limit)
                val end = if (nextLineEnd < 0) code.length else nextLineEnd + 1
                result.append(code, start, end)
                start = end
            } else {
                val blankLineEnd = (lastLineEnd downTo start + CodeHighlightRegionSize / 2).firstOrNull { index ->
                    code[index] == '\n' && (code[index - 1] == '\n' || code[index - 1] == '\r' && code[index - 2] == '\n')
                }
                val end = (blankLineEnd ?: lastLineEnd) + 1
                result.append(highlightRegion(language, code.substring(start, end)))
                start = end
            }
        }
        if (start < code.length) result.append(highlightRegion(language, code.substring(start)))
        return result
    }

    private fun highlightRegion(language: String, code: String): CharSequence = try {
        synchronized(codePrism) { super.highlight(language, code) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: StackOverflowError) {
        logEx(throwable = e, message = "Highlight code region exceeded tokenizer stack")
        code
    } catch (e: Exception) {
        logEx(throwable = e, message = "Highlight code region failed")
        code
    }

    protected override fun highlight(language: String, grammar: Prism4j.Grammar, code: String): CharSequence {
        val jsx = language == "jsx" || language == "tsx"
        val phpMarkup = language == "php" && ExtendedCodeGrammars.hasPhpMarkup(code)
        if (!jsx && !phpMarkup) return super.highlight(language, grammar, code)

        val actualGrammar = if (phpMarkup) prism4j().grammar("php-markup") ?: grammar else grammar
        val nodes = prism4j().tokenize(code, actualGrammar)
        val builder = SpannableStringBuilder(code)
        applyTokens(language, if (jsx) JsxSyntaxTokens.normalize(nodes, prism4j(), grammar, code) else nodes, builder, 0)
        return builder
    }

    private fun applyTokens(language: String, nodes: List<Prism4j.Node>, builder: SpannableStringBuilder, offset: Int) {
        var start = offset
        nodes.forEach { node ->
            val end = start + node.textLength()
            if (node is Prism4j.Syntax && start < end) {
                // 标签容器由各子区域着色，其余嵌套颜色在外层颜色之后应用。
                val firstChild = node.children().firstOrNull()
                val tagContainer = node.type() == "tag" && firstChild is Prism4j.Syntax && firstChild.type() == "tag"
                if (!tagContainer) theme().apply(language, node, builder, start, end)
                if (node.tokenized()) applyTokens(language, node.children(), builder, start)
            }
            start = end
        }
    }
}

@Composable
fun rememberCodeSyntaxHighlight(): CodeSyntaxHighlight =
    if (MaterialTheme.colorScheme.surfaceVariant.luminance() < 0.5f) darkCodeHighlight else lightCodeHighlight

@Composable
fun rememberHighlightedCode(code: String, language: String, editing: Boolean = false): AnnotatedString {
    if (code.length > MaxProcessedCodeLength || language.isBlank()) return remember(code) { AnnotatedString(code) }
    val highlighter = rememberCodeSyntaxHighlight()
    val highlighted by key(language, highlighter) {
        produceState<AnnotatedString?>(null, code, editing) {
            if (editing) delay(180.milliseconds)
            value = withContext(codeHighlightDispatcher) {
                val result = highlighter.highlight(language, code) { ensureActive() }
                AnnotatedString.Builder(code).apply {
                    if (result is Spanned) result.getSpans(0, result.length, ForegroundColorSpan::class.java).forEach { span ->
                        addStyle(SpanStyle(color = Color(span.foregroundColor)), result.getSpanStart(span), result.getSpanEnd(span))
                    }
                }.toAnnotatedString()
            }
        }
    }
    return remember(code, highlighted, editing) {
        val previous = highlighted
        when {
            previous == null -> AnnotatedString(code)
            previous.text == code -> previous
            !editing || previous.spanStyles.isEmpty() -> AnnotatedString(code)
            else -> {
                // 等待重新着色时保留未改动的首尾文字所对应的样式。
                val prefix = code.commonPrefixWith(previous.text).length
                val suffix = min(code.commonSuffixWith(previous.text).length, min(code.length, previous.length) - prefix)
                buildAnnotatedString {
                    append(previous.subSequence(0, prefix))
                    append(code.substring(prefix, code.length - suffix))
                    append(previous.subSequence(previous.length - suffix, previous.length))
                }
            }
        }
    }
}
