package com.allan.picnote.data

import com.allan.picnote.data.entity.DocumentContent

fun markdownEscape(value: String): String = value.replace("\n", " ").replace("\r", " ")
    .replace(Regex("([\\\\`*_{}\\[\\]()<>#+.!|~&-])"), "\\\\$1")

fun DocumentContent.markdown(): String = buildString {
    append("# ").append(markdownEscape(document.title)).append("\n\n")
    var imageNumber = 0
    nodes.forEach { content ->
        if (content.node.kind == "chapter") {
            append("## ").append(markdownEscape(content.node.title)).append("\n\n")
        } else {
            content.images.forEach { image ->
                imageNumber++
                val name = image.name.ifBlank { "图片 $imageNumber" }
                append("![").append(markdownEscape(name)).append("](media/")
                    .append(image.fileName).append(")\n\n")
            }
            if (content.node.bodyMarkdown.isNotBlank()) {
                if (content.node.kind == "introduction") append(content.node.bodyMarkdown.lineSequence().joinToString("\n") { "> $it" })
                else append(content.node.bodyMarkdown)
                append("\n\n")
            }
        }
    }
}.trimEnd() + "\n"
