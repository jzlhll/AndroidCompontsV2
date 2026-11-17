package com.allan.picnote.ui

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
import io.noties.markwon.syntax.SyntaxHighlight
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CodeLanguages = mapOf(
    "java" to "Java", "kotlin" to "Kotlin", "c" to "C", "cpp" to "C++",
    "xml" to "XML", "html" to "HTML", "swift" to "Swift", "go" to "Go",
    "javascript" to "JavaScript", "typescript" to "TypeScript", "bash" to "Bash",
    "python" to "Python", "json" to "JSON", "css" to "CSS", "sql" to "SQL", "yaml" to "YAML",
)

/** 为编辑器与阅读页提供相同的语法着色，失败时保留完整代码。 */
class CodeSyntaxHighlight(dark: Boolean) : SyntaxHighlight {
    private val highlight = Prism4jSyntaxHighlight.create(
        Prism4j(CodeGrammar()),
        if (dark) Prism4jThemeDarkula.create() else Prism4jThemeDefault.create(),
    )

    @Synchronized
    override fun highlight(info: String?, code: String): CharSequence = try {
        val language = info?.trim()?.substringBefore(' ')?.lowercase()
        highlight.highlight(if (language == "c++") "cpp" else language, code)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        logEx(throwable = e, message = "Highlight code failed")
        code
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
    val highlighted by produceState(AnnotatedString(code), code, language, highlight) {
        value = withContext(Dispatchers.Default) {
            val result = highlight.highlight(language, code)
            AnnotatedString.Builder(code).apply {
                if (result is Spanned) result.getSpans(0, result.length, ForegroundColorSpan::class.java).forEach { span ->
                    addStyle(SpanStyle(color = Color(span.foregroundColor)), result.getSpanStart(span), result.getSpanEnd(span))
                }
            }.toAnnotatedString()
        }
    }
    // 新输入尚未着色时先显示原文，避免旧结果覆盖正在输入的字符。
    return if (highlighted.text == code) highlighted else AnnotatedString(code)
}
