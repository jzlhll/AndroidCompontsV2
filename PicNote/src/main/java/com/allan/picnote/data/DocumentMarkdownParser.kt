package com.allan.picnote.data

import com.allan.picnote.logd
import com.allan.picnote.logEx
import kotlinx.coroutines.CancellationException
import org.commonmark.internal.InlineParserImpl
import org.commonmark.node.Block
import org.commonmark.node.CustomBlock
import org.commonmark.node.Document
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Node
import org.commonmark.parser.InlineParserContext
import org.commonmark.parser.Parser
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState
import java.util.UUID

data class MarkdownReference(val label: String, val destination: String, val title: String?)

data class ParsedMarkdownSections(
    val documents: List<Document>,
    val references: List<MarkdownReference>,
    val rawSections: Set<Int> = emptySet(),
)

/** 按节点保留块边界，并使用文档顺序解析跨节点链接和图片引用。 */
fun parseMarkdownSections(sections: List<NodeMarkdown>, checkActive: () -> Unit): ParsedMarkdownSections {
    val marker = ":::picnote-${UUID.randomUUID()}:"
    val parser = Parser.builder().customBlockParserFactory(DocumentNodeBlockParser.Factory(marker)).build()
    val source = buildString {
        sections.forEachIndexed { index, section ->
            checkActive()
            append(marker).append(index).append(MarkdownBlockSeparator).append(section.source)
            if (last() != '\n' && last() != '\r') append('\n')
        }
    }
    val document = parseMarkdown(parser, source)
    if (document != null) {
        val containers = generateSequence(document.firstChild) { it.next }.toList()
        if (containers.size == sections.size && containers.withIndex().all { (index, block) ->
                block is DocumentNodeBlock && block.index == index
            }
        ) {
            val documents = containers.map { container ->
                Document().apply {
                    while (container.firstChild != null) appendChild(container.firstChild)
                }
            }
            return ParsedMarkdownSections(documents, markdownReferences(documents))
        }
    }

    logd("Parse document Markdown by section after losing a node boundary")
    val sectionParser = Parser.builder().build()
    val parsed = sections.map {
        checkActive()
        parseMarkdown(sectionParser, it.source)
    }
    val references = markdownReferences(parsed.filterNotNull())
    val referenceIndex = mutableMapOf<String, LinkReferenceDefinition>()
    references.forEach { reference ->
        referenceIndex.putIfAbsent(reference.label, LinkReferenceDefinition(reference.label, reference.destination, reference.title))
    }
    val referenceParser = Parser.builder().inlineParserFactory { local ->
        // CommonMark 0.13 由 InlineParserFactory 提供整个文档的定义，重复定义仍以最早出现的为准。
        InlineParserImpl(object : InlineParserContext by local {
            override fun getLinkReferenceDefinition(label: String): LinkReferenceDefinition? =
                referenceIndex[label] ?: local.getLinkReferenceDefinition(label)
        })
    }.build()
    val rawSections = mutableSetOf<Int>()
    val documents = parsed.mapIndexed { index, section ->
        checkActive()
        if (section == null) {
            rawSections.add(index)
            Document().apply {
                appendChild(FencedCodeBlock().apply { literal = sections[index].source.removeSuffix(MarkdownBlockSeparator) })
            }
        } else if (references.isEmpty()) section
        else parseMarkdown(referenceParser, sections[index].source) ?: section
    }
    return ParsedMarkdownSections(documents, references, rawSections)
}

private fun parseMarkdown(parser: Parser, source: String): Document? = try {
    parser.parse(source) as Document
} catch (e: CancellationException) {
    throw e
} catch (e: StackOverflowError) {
    logEx(throwable = e, message = "Parse Markdown exceeded parser stack")
    null
} catch (e: Exception) {
    logEx(throwable = e, message = "Parse Markdown failed")
    null
}

private fun markdownReferences(documents: List<Document>): List<MarkdownReference> = buildList {
    val pending = ArrayDeque<Node>()
    documents.forEach { document ->
        pending.addLast(document)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node is LinkReferenceDefinition) add(MarkdownReference(node.label, node.destination, node.title))
            var child = node.lastChild
            while (child != null) {
                pending.addLast(child)
                child = child.previous
            }
        }
    }
}

private class DocumentNodeBlock(val index: Int) : CustomBlock()

/** 先结束上一节点容器，再由 CommonMark 匹配新容器，避免开放块吞下相邻节点。 */
private class DocumentNodeBlockParser(private val node: DocumentNodeBlock, private val marker: String) : AbstractBlockParser() {
    override fun getBlock(): Block = node
    override fun isContainer(): Boolean = true
    override fun canContain(childBlock: Block): Boolean = childBlock !is DocumentNodeBlock

    override fun tryContinue(state: ParserState): BlockContinue? =
        if (state.line.startsWith(marker)) BlockContinue.none() else BlockContinue.atIndex(state.index)

    class Factory(private val marker: String) : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            val line = state.line
            if (state.index != 0 || !line.startsWith(marker)) return BlockStart.none()
            val index = line.substring(marker.length).toIntOrNull() ?: return BlockStart.none()
            return BlockStart.of(DocumentNodeBlockParser(DocumentNodeBlock(index), marker)).atIndex(line.length)
        }
    }
}
