package com.allan.picnote.data

import android.content.res.Resources
import androidx.core.content.ContextCompat
import com.allan.picnote.R
import com.allan.picnote.data.entity.DocumentContent
import com.au.module_android.Globals

const val MarkdownBlockSeparator = "\n\n"

data class NodeMarkdown(val nodeId: String, val kind: String, val hasImages: Boolean, val source: String)

fun markdownEscape(value: String): String = value.replace("\n", " ").replace("\r", " ")
    .replace(Regex("([\\\\`*_{}\\[\\]()<>#+.!|~&-])"), "\\\\$1")

fun DocumentContent.markdown(): String {
    val source = markdownNodes().joinToString("") { it.source }.removeSuffix(MarkdownBlockSeparator)
    return if (source.endsWith('\n') || source.endsWith('\r')) source else "$source\n"
}

fun DocumentContent.markdownNodes(
    resources: Resources = ContextCompat.getContextForLanguage(Globals.app).resources,
): List<NodeMarkdown> = buildList {
    val title = nodes.first { it.node.kind == "title" }
    add(NodeMarkdown(title.node.id, "title", false, "# ${markdownEscape(document.title)}$MarkdownBlockSeparator"))
    var imageNumber = 0
    nodes.forEach { content ->
        if (content.node.kind == "title") return@forEach
        val source = buildString {
            if (content.node.kind == "chapter") {
                append("## ").append(markdownEscape(content.node.title)).append(MarkdownBlockSeparator)
            } else {
                content.images.forEach { image ->
                    val alt = resources.getString(R.string.image_number, ++imageNumber)
                    append("![").append(markdownEscape(alt)).append("](media/")
                        .append(image.fileName).append(')').append(MarkdownBlockSeparator)
                }
                if (content.node.bodyMarkdown.isNotBlank()) {
                    if (content.node.kind == "introduction") append(content.node.bodyMarkdown.lineSequence().joinToString("\n") { "> $it" })
                    else append(content.node.bodyMarkdown)
                    append(MarkdownBlockSeparator)
                }
            }
        }
        if (source.isNotEmpty()) add(NodeMarkdown(content.node.id, content.node.kind, content.images.isNotEmpty(), source))
    }
}
