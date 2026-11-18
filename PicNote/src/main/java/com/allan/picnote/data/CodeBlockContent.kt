package com.allan.picnote.data

import com.allan.picnote.logEx
import org.commonmark.node.FencedCodeBlock
import org.commonmark.parser.Parser

private val codeFenceLine = Regex("(?m)^( {0,3})(`{3,}|~{3,})([^\\r\\n]*)")
private val fenceRuns = Regex("`+|~+")
private val descriptionSeparator = Regex("(?:\\r\\n|\\r|\\n){1,2}\\z")
private val codeBlockParser = Parser.builder().build()

val CodeLineBreak = Regex("\\r\\n|\\r|\\n")
const val MaxProcessedCodeLength = 128 * 1024

/** 用标准 Markdown 保存代码块草稿，保留代码空白与围栏信息。 */
data class CodeBlockContent(
    val description: String = "",
    val code: String = "",
    val language: String = "",
    val info: String = language,
) {
    fun markdown(): String {
        val fenceChar = if ('`' in info) '~' else '`'
        var fenceLength = 3
        fenceRuns.findAll(description).plus(fenceRuns.findAll(code)).forEach { match ->
            if (match.value.first() == fenceChar && match.value.length >= fenceLength) fenceLength = match.value.length + 1
        }
        val fence = fenceChar.toString().repeat(fenceLength)
        val newline = CodeLineBreak.find(code)?.value ?: CodeLineBreak.find(description)?.value ?: "\n"
        return buildString {
            if (description.isNotBlank()) {
                val separator = if (description.endsWith('\r') && newline == "\n") "\r" else newline
                append(description).append(separator).append(separator)
            }
            append(fence).append(info).append(newline).append(code)
            append(if (code.endsWith('\r') && newline == "\n") "\r" else newline).append(fence)
        }
    }

    fun withLanguage(value: String): CodeBlockContent {
        if (value == language) return this
        val token = CodeLanguageToken.find(info)
        val updatedInfo = when {
            token == null -> value
            value.isEmpty() -> ""
            else -> info.replaceRange(token.range, value)
        }
        return copy(language = value, info = updatedInfo)
    }
}

fun parseCodeBlock(markdown: String): CodeBlockContent? {
    val trimmed = markdown.trimEnd()
    val fences = codeFenceLine.findAll(trimmed).toList()
    val closing = fences.lastOrNull() ?: return null
    if (closing.range.last != trimmed.lastIndex || closing.groupValues[3].isNotBlank()) return null
    val document = try {
        codeBlockParser.parse(markdown)
    } catch (e: StackOverflowError) {
        logEx(throwable = e, message = "Parse code block exceeded parser stack")
        return null
    }
    val parsed = document.lastChild as? FencedCodeBlock ?: return null
    // 缩进围栏由 CommonMark 改写每行的缩进，在原文模式中保留完整布局。
    if (parsed.fenceIndent != 0 || closing.groupValues[2].first() != parsed.fenceChar ||
        closing.groupValues[2].length < parsed.fenceLength
    ) return null
    val codeStart = if (parsed.literal.isEmpty()) closing.range.first else {
        var offset = closing.range.first
        var linesLeft = parsed.literal.count { it == '\n' }
        while (offset > 0 && linesLeft > 0) {
            offset -= 1
            if (markdown[offset] == '\n' || markdown[offset] == '\r') {
                if (markdown[offset] == '\n' && markdown.getOrNull(offset - 1) == '\r') offset -= 1
                linesLeft -= 1
            }
        }
        while (offset > 0 && markdown[offset - 1] !in "\r\n") offset -= 1
        offset
    }
    val opening = fences.asReversed().drop(1).firstOrNull { fence ->
        val newlineStart = fence.range.last + 1
        codeStart - newlineStart in 1..2 && CodeLineBreak.matches(markdown.substring(newlineStart, codeStart))
    } ?: return null
    if (opening.groupValues[1].isNotEmpty() || opening.groupValues[2].first() != parsed.fenceChar ||
        opening.groupValues[2].length != parsed.fenceLength || (parsed.fenceChar == '`' && '`' in opening.groupValues[3])
    ) return null
    val fencedText = markdown.substring(codeStart, closing.range.first)
    if (CodeLineBreak.replace(fencedText, "\n") != parsed.literal) return null
    val separatorLength = when {
        fencedText.endsWith("\r\n") -> 2
        fencedText.endsWith('\n') || fencedText.endsWith('\r') -> 1
        else -> 0
    }
    val description = descriptionSeparator.replace(markdown.substring(0, opening.range.first), "")
    return CodeBlockContent(
        description = description,
        code = fencedText.dropLast(separatorLength),
        language = CodeLanguageToken.find(parsed.info.orEmpty())?.value.orEmpty(),
        info = opening.groupValues[3],
    )
}
