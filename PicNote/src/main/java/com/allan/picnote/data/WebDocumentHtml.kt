package com.allan.picnote.data

import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.coroutines.ensureActive
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.renderer.html.CoreHtmlNodeRenderer
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

/** 使用标准 Markdown 节点导出，仅将当前文档内的可信图片写入 HTML。 */
class WebDocumentHtml(private val images: Map<String, File>, private val coroutineContext: CoroutineContext) {
    private val renderer = HtmlRenderer.builder().escapeHtml(true).softbreak("<br>")
        .nodeRendererFactory { context ->
            val html = context.writer
            object : CoreHtmlNodeRenderer(context) {
                override fun getNodeTypes(): Set<Class<out Node>> = setOf(Image::class.java, Link::class.java)

                override fun visit(image: Image) {
                    val alt = buildString {
                        image.accept(object : AbstractVisitor() {
                            override fun visit(text: Text) { append(text.literal) }
                            override fun visit(code: Code) { append(code.literal) }
                            override fun visit(lineBreak: SoftLineBreak) { append(' ') }
                            override fun visit(lineBreak: HardLineBreak) { append(' ') }
                        })
                    }
                    val file = images[image.destination]
                    if (file == null) {
                        html.text(alt)
                        return
                    }
                    html.raw("<img alt=\"${htmlEscape(alt)}\"")
                    image.title?.let { html.raw(" title=\"${htmlEscape(it)}\"") }
                    html.raw(" src=\"data:image/webp;base64,")
                    val output = object : OutputStream() {
                        override fun write(value: Int) { html.raw((value and 0xff).toChar().toString()) }
                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            coroutineContext.ensureActive()
                            html.raw(String(bytes, offset, length, Charsets.US_ASCII))
                        }
                    }
                    Base64OutputStream(output, Base64.NO_WRAP).use { encoded ->
                        file.inputStream().use { it.copyTo(encoded) }
                    }
                    html.raw("\" />")
                }

                override fun visit(link: Link) {
                    val scheme = link.destination.filterNot { it <= ' ' || it == '\u007f' }
                        .substringBefore('/').substringBefore('?').substringBefore('#')
                    val protocol = scheme.substringBefore(':', "").lowercase()
                    // CommonMark 0.13 不校验 URL 协议。
                    if (':' in scheme && protocol !in setOf("http", "https", "mailto")) visitChildren(link)
                    else super.visit(link)
                }
            }
        }.build()

    fun write(sections: List<NodeMarkdown>, output: Appendable) {
        val parsed = parseMarkdownSections(sections) { coroutineContext.ensureActive() }
        parsed.documents.forEach { document ->
            coroutineContext.ensureActive()
            renderer.render(document, output)
        }
    }
}

fun htmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;")
