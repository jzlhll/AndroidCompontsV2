package com.allan.picnote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.*
import org.commonmark.parser.Parser

/** 从 CommonMark 语法树提取正文，保留代码和转义后的实际文字。 */
fun markdownPlainText(node: Node): String = buildString {
    node.accept(object : AbstractVisitor() {
        override fun visit(text: Text) { append(text.literal) }
        override fun visit(code: Code) { append(code.literal) }
        override fun visit(code: FencedCodeBlock) { append(code.literal); append('\n') }
        override fun visit(code: IndentedCodeBlock) { append(code.literal); append('\n') }
        override fun visit(line: SoftLineBreak) { append('\n') }
        override fun visit(line: HardLineBreak) { append('\n') }
        override fun visit(paragraph: Paragraph) { visitChildren(paragraph); append('\n') }
        override fun visit(heading: Heading) { visitChildren(heading); append('\n') }
        override fun visit(html: HtmlBlock) { append(html.literal); append('\n') }
        override fun visit(html: HtmlInline) { append(html.literal) }
    })
}.trimEnd('\n')

@Composable
fun rememberCompactMarkdown(markdown: String): String {
    val text by produceState("", markdown) {
        value = ""
        value = withContext(Dispatchers.Default) { markdownPlainText(Parser.builder().build().parse(markdown)) }
    }
    return text
}
