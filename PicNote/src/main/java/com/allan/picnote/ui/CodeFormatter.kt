package com.allan.picnote.ui

import com.allan.picnote.data.CodeLineBreak
import com.allan.picnote.data.MaxProcessedCodeLength
import com.allan.picnote.data.normalizeCodeLanguage
import io.noties.prism4j.Prism4j

private val lineBreakCharacters = charArrayOf('\r', '\n')
private val xmlAttribute = Regex("""\s+([^\s=/>]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
private const val ContinuationOperators = ".+-=*/%&|?^\\"

/** 保存前整理代码结构与缩进，字符串、注释和有语义的相对缩进保持原文。 */
fun formatCodeForSave(code: String, language: String): String {
    val name = normalizeCodeLanguage(language)
    if (code.isBlank() || code.length > MaxProcessedCodeLength || name !in setOf("java", "kotlin", "c", "cpp", "swift", "go", "javascript", "typescript", "json", "css", "xml", "html", "markup")) return code
    val newline = CodeLineBreak.find(code)?.value ?: "\n"
    if (CodeLineBreak.findAll(code).any { it.value != newline }) return code
    val prism = Prism4j(CodeGrammar())
    val grammar = prism.grammar(name) ?: return code
    val tokens = prism.tokenize(code, grammar)
    return if (name in setOf("xml", "html", "markup")) formatMarkup(code, tokens, name == "html", newline)
    else formatBracedCode(code, tokens, name, newline)
}

private fun formatBracedCode(code: String, tokens: List<Prism4j.Node>, language: String, lineBreak: String): String {
    val protectedEnds = IntArray(code.length)
    val protectedTypes = setOf("string", "char", "comment", "regex", "template-string", "property", "url")
    fun protect(nodes: List<Prism4j.Node>, start: Int) {
        var offset = start
        nodes.forEach { node ->
            val end = offset + node.textLength()
            if (node is Prism4j.Syntax) {
                if (node.type() in protectedTypes) protectedEnds[offset] = end
                else protect(node.children(), offset)
            }
            offset = end
        }
    }
    protect(tokens, 0)
    if (language in setOf("java", "kotlin", "swift")) {
        Regex("\"\"\"[\\s\\S]*?\"\"\"").findAll(code).forEach { literal ->
            if (protectedEnds[literal.range.first] < literal.range.last + 1) protectedEnds[literal.range.first] = literal.range.last + 1
        }
    }
    if (language == "cpp") {
        Regex("R\"([^\\s()\\\\]{0,16})\\([\\s\\S]*?\\)\\1\"").findAll(code).forEach { literal ->
            protectedEnds[literal.range.first] = literal.range.last + 1
        }
    }
    // Swift 原始字符串的插值和分隔符无需参与结构整理。
    if (language == "swift" && "#\"" in code) return code
    if (language == "c" || language == "cpp") {
        // 宏的反斜杠续行必须作为整体保留，不能按分号或花括号换行。
        Regex("(?m)^[\\t ]*#").findAll(code).forEach { directive ->
            val start = directive.range.first
            var lineStart = start
            var newline = CodeLineBreak.find(code, lineStart)
            while (newline != null && code.substring(lineStart, newline.range.first).trimEnd().endsWith('\\')) {
                lineStart = newline.range.last + 1
                newline = CodeLineBreak.find(code, lineStart)
            }
            protectedEnds[start] = newline?.range?.last?.plus(1) ?: code.length
        }
    }
    val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    val brackets = ArrayDeque<Char>()
    var index = 0
    while (index < code.length) {
        val protectedEnd = protectedEnds[index]
        if (protectedEnd > index) { index = protectedEnd; continue }
        when (val char = code[index]) {
            '(', '[', '{' -> brackets.addLast(char)
            ')', ']', '}' -> if (brackets.removeLastOrNull() != pairs[char]) return code
            '\'', '"', '`' -> return code
        }
        index += 1
    }
    if (brackets.isNotEmpty()) return code

    val output = StringBuilder()
    val json = language == "json"
    // Go 的复合字面量在换行后要求尾逗号，只整理已有行的缩进。
    val expandLines = language != "go"
    val joinedKeywords = setOf("else", "catch", "finally", "while")
    val newlineAfterBlock = language in setOf("java", "c", "cpp", "css")
    var pendingSpace = false
    var syntheticBreak = false
    var sourceLineStart = 0
    var sourceLineHasText = false
    var preserveContinuationIndent = false
    var lastSourceToken: Char? = null
    fun write(value: String, offset: Int, structuralToken: Char? = null) {
        if (output.isEmpty() || output.last() in lineBreakCharacters) {
            if (!json && !sourceLineHasText && (preserveContinuationIndent || code[offset] == '.')) {
                output.append(code, sourceLineStart, offset)
            } else output.append("    ".repeat(brackets.size))
        } else if (pendingSpace) output.append(' ')
        output.append(value)
        syntheticBreak = false
        pendingSpace = false
        sourceLineHasText = true
        lastSourceToken = structuralToken
    }
    fun newline() {
        pendingSpace = false
        if (output.isNotEmpty() && output.last() !in lineBreakCharacters) {
            output.append(lineBreak)
            syntheticBreak = true
        }
    }
    fun sourceNewline(start: Int, end: Int) {
        if (syntheticBreak) output.setLength(output.length - lineBreak.length)
        if (!sourceLineHasText) output.append(code, sourceLineStart, start)
        output.append(code, start, end)
        syntheticBreak = false
        pendingSpace = false
        if (sourceLineHasText) preserveContinuationIndent = lastSourceToken?.let { it in ContinuationOperators } == true
        sourceLineHasText = false
        sourceLineStart = end
        lastSourceToken = null
    }
    fun nextContent(after: Int): Int {
        var next = after
        while (next < code.length && code[next].isWhitespace()) next += 1
        return next
    }
    index = 0
    while (index < code.length) {
        val protectedEnd = protectedEnds[index]
        if (protectedEnd > index) {
            write(code.substring(index, protectedEnd), index)
            if (code[protectedEnd - 1] in lineBreakCharacters) {
                sourceLineHasText = false
                sourceLineStart = protectedEnd
                preserveContinuationIndent = false
            }
            index = protectedEnd
            continue
        }
        val char = code[index]
        when {
            char in lineBreakCharacters -> {
                val end = index + if (char == '\r' && code.getOrNull(index + 1) == '\n') 2 else 1
                sourceNewline(index, end)
                index = end
                continue
            }
            char.isWhitespace() -> pendingSpace = true
            char == '{' || (json && char == '[') -> {
                if (expandLines && !json && output.isNotEmpty() && output.last() !in "\r\n ([{") pendingSpace = true
                write(char.toString(), index, char)
                brackets.addLast(char)
                val next = nextContent(index + 1)
                val closing = if (char == '{') '}' else ']'
                if (expandLines && code.getOrNull(next) != closing) newline()
            }
            char == '}' || (json && char == ']') -> {
                brackets.removeLast()
                var previous = index - 1
                while (previous >= 0 && code[previous].isWhitespace()) previous -= 1
                val opening = if (char == '}') '{' else '['
                if (expandLines && code.getOrNull(previous) != opening) newline() else pendingSpace = false
                write(char.toString(), index, char)
                val next = nextContent(index + 1)
                val nextChar = code.getOrNull(next)
                var wordEnd = next
                while (wordEnd < code.length && code[wordEnd].isLetter()) wordEnd += 1
                val word = code.substring(next, wordEnd)
                if (expandLines && (nextChar == '}' || (json && nextChar == ']'))) newline()
                else if (word in joinedKeywords) pendingSpace = true
                else if (newlineAfterBlock && nextChar?.isLetter() == true) newline()
            }
            char == '(' || char == '[' -> { write(char.toString(), index, char); brackets.addLast(char) }
            char == ')' || char == ']' -> { brackets.removeLast(); pendingSpace = false; write(char.toString(), index, char) }
            char == ';' -> {
                pendingSpace = false
                write(";", index, char)
                if (expandLines && '(' !in brackets && '[' !in brackets) newline()
            }
            json && char == ',' -> { pendingSpace = false; write(",", index, char); newline() }
            json && char == ':' -> { pendingSpace = false; write(":", index, char); pendingSpace = true }
            else -> write(char.toString(), index, char)
        }
        index += 1
    }
    if (syntheticBreak) output.setLength(output.length - lineBreak.length)
    if (!sourceLineHasText) output.append(code, sourceLineStart, code.length)
    return output.toString()
}

/** 保留混合文本节点原文，只重排由标签组成的结构。 */
private class MarkupElement(val name: String, val start: Int, val opening: String, var end: Int) {
    val children = mutableListOf<MarkupElement>()
    var closing = ""
    var hasText = name in setOf("pre", "textarea", "script", "style")
}

private fun formatMarkup(code: String, tokens: List<Prism4j.Node>, html: Boolean, newline: String): String {
    val root = MarkupElement("", 0, "", code.length)
    val elements = ArrayDeque<MarkupElement>().apply { addLast(root) }
    val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    val inlineTags = setOf("a", "abbr", "b", "bdi", "bdo", "br", "button", "cite", "code", "data", "del", "dfn", "em", "i", "img", "input", "ins", "kbd", "label", "mark", "q", "s", "samp", "select", "small", "span", "strong", "sub", "sup", "textarea", "time", "u", "var", "wbr")
    val tagName = Regex("^</?([A-Za-z_][\\w:.-]*)")
    var offset = 0
    tokens.forEach { token ->
        val end = offset + token.textLength()
        val raw = code.substring(offset, end)
        val parent = elements.last()
        if (token is Prism4j.Syntax && token.type() == "tag") {
            val match = tagName.find(raw) ?: return code
            val name = match.groupValues[1].let { if (html) it.lowercase() else it }
            if (raw.startsWith("</")) {
                if (elements.size == 1 || parent.name != name) return code
                parent.closing = raw
                parent.end = end
                elements.removeLast()
            } else {
                // 内联元素之间新增空白会改变文本，例如相邻 span 原本没有空格。
                if (html && name in inlineTags) parent.hasText = true
                val element = MarkupElement(name, offset, raw, end)
                if (!html && "xml:space" in raw) {
                    // 整段保留也覆盖继承该属性的子节点，其他属性中的引号文本不参与判断。
                    element.hasText = element.hasText || xmlAttribute.findAll(raw).any { attribute ->
                        attribute.groupValues[1] == "xml:space" &&
                            (attribute.groups[2] ?: attribute.groups[3])?.value != "default"
                    }
                }
                parent.children.add(element)
                if (!raw.endsWith("/>") && !(html && name in voidTags)) elements.addLast(element)
            }
        } else if (token is Prism4j.Syntax && token.type() in setOf("comment", "prolog", "doctype")) {
            parent.children.add(MarkupElement("", offset, raw, end))
        } else if (raw.isNotBlank()) parent.hasText = true
        offset = end
    }
    if (elements.size != 1 || root.hasText) return code
    val output = StringBuilder()
    fun appendGap(start: Int, end: Int, insertBreak: Boolean) {
        val lastBreak = code.substring(start, end).lastIndexOfAny(lineBreakCharacters)
        if (lastBreak >= 0) output.append(code, start, start + lastBreak + 1)
        else if (insertBreak) output.append(newline)
    }
    fun render(element: MarkupElement, depth: Int) {
        val indent = "    ".repeat(depth)
        output.append(indent)
        if (element.hasText) {
            output.append(code, element.start, element.end)
            return
        }
        output.append(element.opening)
        val contentStart = element.start + element.opening.length
        val closingStart = element.end - element.closing.length
        if (element.children.isEmpty()) {
            output.append(code, contentStart, closingStart)
        } else {
            var previousEnd = contentStart
            element.children.forEach { child ->
                appendGap(previousEnd, child.start, insertBreak = true)
                render(child, depth + 1)
                previousEnd = child.end
            }
            appendGap(previousEnd, closingStart, insertBreak = true)
            output.append(indent)
        }
        output.append(element.closing)
    }
    var previousEnd = 0
    root.children.forEach { element ->
        appendGap(previousEnd, element.start, insertBreak = previousEnd > 0)
        render(element, 0)
        previousEnd = element.end
    }
    output.append(code, previousEnd, code.length)
    return output.toString()
}
