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
import com.allan.picnote.logEx
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CodeHighlightRegionSize = 8 * 1024

/** 为编辑器与阅读页提供相同的语法着色，失败时保留完整代码。 */
class CodeSyntaxHighlight(dark: Boolean) : Prism4jSyntaxHighlight(
    Prism4j(CodeGrammar()),
    if (dark) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create(),
    null,
) {
    @Synchronized
    override fun highlight(info: String?, code: String): CharSequence {
        val language = info?.trim()?.takeWhile { !it.isWhitespace() }?.lowercase().orEmpty()
        if (language.isEmpty() || code.isEmpty()) return code
        val name = if (language == "c++") "cpp" else language
        if (code.length <= CodeHighlightRegionSize) return highlightRegion(name, code)

        // 优先在空行处分区。单行超过上限时整行用纯文本，下一行重新着色。
        val result = SpannableStringBuilder()
        var start = 0
        while (code.length - start > CodeHighlightRegionSize) {
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
                result.append(highlightRegion(name, code.substring(start, end)))
                start = end
            }
        }
        if (start < code.length) result.append(highlightRegion(name, code.substring(start)))
        return result
    }

    private fun highlightRegion(language: String, code: String): CharSequence = try {
        val result = super.highlight(language, code)
        if (code.contentEquals(result)) result else code
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
fun rememberCodeSyntaxHighlight(): CodeSyntaxHighlight {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(dark) { CodeSyntaxHighlight(dark) }
}

@Composable
fun rememberHighlightedCode(code: String, language: String): AnnotatedString {
    val highlight = rememberCodeSyntaxHighlight()
    val highlighted by key(language, highlight) {
        produceState(AnnotatedString(code), code) {
            value = withContext(Dispatchers.Default) {
                val result = highlight.highlight(language, code)
                AnnotatedString.Builder(code).apply {
                    if (result is Spanned) result.getSpans(0, result.length, ForegroundColorSpan::class.java).forEach { span ->
                        addStyle(SpanStyle(color = Color(span.foregroundColor)), result.getSpanStart(span), result.getSpanEnd(span))
                    }
                }.toAnnotatedString()
            }
        }
    }
    // 新输入尚未着色时先显示原文，避免旧结果覆盖正在输入的字符。
    return if (highlighted.text == code) highlighted else AnnotatedString(code)
}
