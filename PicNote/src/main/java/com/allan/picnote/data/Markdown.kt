package com.allan.picnote.data

import com.allan.picnote.data.entity.DocumentContent

data class NodeMarkdown(val nodeId: String, val kind: String, val hasImages: Boolean, val source: String)

fun markdownEscape(value: String): String = value.replace("\n", " ").replace("\r", " ")
    .replace(Regex("([\\\\`*_{}\\[\\]()<>#+.!|~&-])"), "\\\\$1")

fun DocumentContent.markdown(): String = markdownNodes().joinToString("") { it.source }.trimEnd() + "\n"

fun DocumentContent.markdownNodes(): List<NodeMarkdown> = buildList {
    val title = nodes.first { it.node.kind == "title" }
    add(NodeMarkdown(title.node.id, "title", false, "# ${markdownEscape(document.title)}\n\n"))
    var imageNumber = 0
    nodes.forEach { content ->
        if (content.node.kind == "title") return@forEach
        val source = buildString {
            if (content.node.kind == "chapter") {
                append("## ").append(markdownEscape(content.node.title)).append("\n\n")
            } else {
                content.images.forEach { image ->
                    imageNumber++
                    append("![图片 ").append(imageNumber).append("](media/")
                        .append(image.fileName).append(")\n\n")
                }
                if (content.node.bodyMarkdown.isNotBlank()) {
                    if (content.node.kind == "introduction") append(content.node.bodyMarkdown.lineSequence().joinToString("\n") { "> $it" })
                    else append(content.node.bodyMarkdown)
                    append("\n\n")
                }
            }
        }
        if (source.isNotEmpty()) add(NodeMarkdown(content.node.id, content.node.kind, content.images.isNotEmpty(), source))
    }
}
