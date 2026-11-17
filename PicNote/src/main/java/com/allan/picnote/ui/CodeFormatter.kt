package com.allan.picnote.ui

import io.noties.prism4j.Prism4j

/** 保存前整理代码结构与缩进，字符串、注释和有语义的相对缩进保持原文。 */
fun formatCodeForSave(code: String, language: String): String {
    val name = when (val value = language.lowercase()) {
        "js" -> "javascript"
        "ts" -> "typescript"
        "c++" -> "cpp"
        else -> value
    }
    if (code.isBlank() || name !in setOf("java", "kotlin", "c", "cpp", "swift", "go", "javascript", "typescript", "json", "css", "xml", "html", "markup")) return code
    val prism = Prism4j(CodeGrammar())
    val grammar = prism.grammar(name) ?: return code
    val tokens = prism.tokenize(code, grammar)
    val formatted = if (name in setOf("xml", "html", "markup")) formatMarkup(code, tokens, name == "html")
    else formatBracedCode(code, tokens, name)
    if (formatted == code) return code
    return formatted.trimEnd('\n') + if (code.endsWith('\n')) "\n" else ""
}

private fun formatBracedCode(code: String, tokens: List<Prism4j.Node>, language: String): String {
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
            protectedEnds[literal.range.first] = literal.range.last + 1
        }
    }
    if (language == "c" || language == "cpp") {
        // 宏的反斜杠续行必须作为整体保留，不能按分号或花括号换行。
        Regex("(?m)^[\\t ]*#").findAll(code).forEach { directive ->
            val start = directive.range.first
            var end = code.indexOf('\n', start).let { if (it < 0) code.length else it }
            while (end < code.length && code.substring(start, end).trimEnd().endsWith('\\')) {
                end = code.indexOf('\n', end + 1).let { if (it < 0) code.length else it }
            }
            protectedEnds[start] = if (end < code.length) end + 1 else end
        }
    }
    val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    val brackets = mutableListOf<Char>()
    var index = 0
    while (index < code.length) {
        val protectedEnd = protectedEnds[index]
        if (protectedEnd > index) { index = protectedEnd; continue }
        when (val char = code[index]) {
            '(', '[', '{' -> brackets.add(char)
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
    fun write(value: String) {
        if (output.isEmpty() || output.last() == '\n') output.append("    ".repeat(brackets.size))
        else if (pendingSpace) output.append(' ')
        output.append(value)
        pendingSpace = false
    }
    fun newline() {
        pendingSpace = false
        if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
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
            write(code.substring(index, protectedEnd))
            index = protectedEnd
            continue
        }
        val char = code[index]
        when {
            char == '\n' -> newline()
            char.isWhitespace() -> pendingSpace = true
            char == '{' || (json && char == '[') -> {
                if (expandLines && !json && output.isNotEmpty() && output.last() !in "\n ([{") pendingSpace = true
                write(char.toString())
                brackets.add(char)
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
                write(char.toString())
                val next = nextContent(index + 1)
                val nextChar = code.getOrNull(next)
                var wordEnd = next
                while (wordEnd < code.length && code[wordEnd].isLetter()) wordEnd += 1
                val word = code.substring(next, wordEnd)
                if (expandLines && (nextChar == '}' || (json && nextChar == ']'))) newline()
                else if (word in joinedKeywords) pendingSpace = true
                else if (newlineAfterBlock && nextChar?.isLetter() == true) newline()
            }
            char == '(' || char == '[' -> { write(char.toString()); brackets.add(char) }
            char == ')' || char == ']' -> { brackets.removeLast(); pendingSpace = false; write(char.toString()) }
            char == ';' -> {
                pendingSpace = false
                write(";")
                if (expandLines && '(' !in brackets && '[' !in brackets) newline()
            }
            json && char == ',' -> { pendingSpace = false; write(","); newline() }
            json && char == ':' -> { pendingSpace = false; write(":"); pendingSpace = true }
            else -> write(char.toString())
        }
        index += 1
    }
    return output.toString()
}

/** 保留混合文本节点原文，只重排由标签组成的结构。 */
private class MarkupElement(val name: String, val start: Int, val opening: String, var end: Int) {
    val children = mutableListOf<MarkupElement>()
    var closing = ""
    var hasText = name in setOf("pre", "textarea", "script", "style")
}

private fun formatMarkup(code: String, tokens: List<Prism4j.Node>, html: Boolean): String {
    val root = MarkupElement("", 0, "", code.length)
    val elements = mutableListOf(root)
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
                parent.children.add(element)
                if (!raw.endsWith("/>") && !(html && name in voidTags)) elements.add(element)
            }
        } else if (token is Prism4j.Syntax && token.type() in setOf("comment", "prolog", "doctype")) {
            parent.children.add(MarkupElement("", offset, raw, end))
        } else if (raw.isNotBlank()) parent.hasText = true
        offset = end
    }
    if (elements.size != 1 || root.hasText) return code
    fun render(element: MarkupElement, depth: Int): String {
        val indent = "    ".repeat(depth)
        if (element.hasText) return indent + code.substring(element.start, element.end)
        if (element.children.isEmpty()) return indent + element.opening + element.closing
        return buildString {
            append(indent).append(element.opening).append('\n')
            append(element.children.joinToString("\n") { render(it, depth + 1) }).append('\n')
            append(indent).append(element.closing)
        }
    }
    return root.children.joinToString("\n") { render(it, 0) }
}
