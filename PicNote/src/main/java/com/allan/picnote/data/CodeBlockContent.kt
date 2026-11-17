package com.allan.picnote.data

/** 用标准 Markdown 保存代码块草稿，保留代码空白与语言标记。 */
data class CodeBlockContent(val description: String = "", val code: String = "", val language: String = "") {
    fun markdown(): String {
        var fenceLength = 3
        Regex("`+").findAll(code).forEach { match ->
            if (match.value.length >= fenceLength) fenceLength = match.value.length + 1
        }
        val fence = "`".repeat(fenceLength)
        return buildString {
            if (description.isNotBlank()) append(description.trimEnd()).append("\n\n")
            append(fence).append(language).append('\n').append(code).append('\n').append(fence)
        }
    }
}

fun parseCodeBlock(markdown: String): CodeBlockContent? {
    val closingStart = markdown.lastIndexOf('\n')
    if (closingStart < 0) return null
    val fence = markdown.substring(closingStart + 1)
    if (fence.length < 3 || fence.any { it != '`' }) return null
    val openingStart = markdown.lastIndexOf("\n$fence", closingStart - 1).let { index ->
        if (index >= 0) index + 1 else if (markdown.startsWith(fence)) 0 else return null
    }
    val openingEnd = markdown.indexOf('\n', openingStart)
    if (openingEnd < 0 || openingEnd >= closingStart) return null
    val language = markdown.substring(openingStart + fence.length, openingEnd)
    if (language.any { it == '`' || it.isWhitespace() }) return null
    return CodeBlockContent(
        description = markdown.substring(0, openingStart).trimEnd(),
        code = markdown.substring(openingEnd + 1, closingStart),
        language = language,
    )
}
