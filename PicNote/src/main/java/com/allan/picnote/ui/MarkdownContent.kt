package com.allan.picnote.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.allan.picnote.data.entity.NodeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
fun rememberNodePreviews(nodes: List<NodeContent>): Map<String, String> {
    val previews by produceState<Map<String, String>>(emptyMap(), nodes) {
        val previous = value
        value = withContext(Dispatchers.Default) {
            val parser = Parser.builder().build()
            buildMap {
                nodes.forEach { content ->
                    currentCoroutineContext().ensureActive()
                    val markdown = content.node.bodyMarkdown
                    if (markdown.isNotBlank() && !containsKey(markdown)) {
                        put(markdown, previous[markdown] ?: markdownPlainText(parser.parse(markdown)))
                    }
                }
            }
        }
    }
    return previews
}
