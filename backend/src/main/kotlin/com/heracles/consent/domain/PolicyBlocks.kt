package com.heracles.consent.domain

import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.Link as MarkdownLink
import org.commonmark.parser.Parser

/** What a person reads: paragraphs, headings and lists made of text, emphasis and links. Nothing else can exist. */
sealed interface Inline {
    data class Plain(val text: String) : Inline
    data class Strong(val children: List<Inline>) : Inline
    data class Emphasis(val children: List<Inline>) : Inline
    data class Link(val href: String, val children: List<Inline>) : Inline
    data object LineBreak : Inline
}

sealed interface Block {
    data class Paragraph(val children: List<Inline>) : Block
    data class Heading(val level: Int, val children: List<Inline>) : Block
    data class ListBlock(val ordered: Boolean, val items: List<List<Inline>>) : Block
}

/** The text uses something outside the restricted format. The message names what, never repeats the text. */
class InvalidPolicyTextException(val reason: String) : IllegalArgumentException(reason)

/**
 * Reads the restricted Markdown an administrator writes and turns it into [Block]s (spec RF1, plan section 7). HTML, images,
 * code, quotes, tables and links other than `https` and `mailto` are refused on writing; clients draw the blocks themselves,
 * with no Markdown library and no `innerHTML`.
 */
object PolicyBlocks {
    private val parser: Parser = Parser.builder().build()
    private val allowedSchemes = setOf("https", "mailto")
    private const val MAX_DEPTH = 4

    fun parse(markdown: String): List<Block> {
        val document = parser.parse(markdown)
        val blocks = mutableListOf<Block>()
        var node: Node? = document.firstChild
        while (node != null) {
            blocks += block(node)
            node = node.next
        }
        return blocks
    }

    private fun block(node: Node): Block = when (node) {
        is Paragraph -> Block.Paragraph(inlines(node))
        is Heading -> Block.Heading(node.level, inlines(node))
        is BulletList -> Block.ListBlock(ordered = false, items = items(node))
        is OrderedList -> Block.ListBlock(ordered = true, items = items(node))
        else -> throw InvalidPolicyTextException("block_not_allowed")
    }

    private fun items(list: Node): List<List<Inline>> {
        val items = mutableListOf<List<Inline>>()
        var item: Node? = list.firstChild
        while (item != null) {
            if (item !is ListItem) throw InvalidPolicyTextException("block_not_allowed")
            // A list item holds one paragraph of inline content; nested lists and other blocks are not part of the format.
            val paragraph = item.firstChild
            if (paragraph !is Paragraph || paragraph.next != null) throw InvalidPolicyTextException("block_not_allowed")
            items += inlines(paragraph)
            item = item.next
        }
        return items
    }

    private fun inlines(parent: Node, depth: Int = 0): List<Inline> {
        if (depth > MAX_DEPTH) throw InvalidPolicyTextException("too_deep")
        val result = mutableListOf<Inline>()
        var node: Node? = parent.firstChild
        while (node != null) {
            result += when (node) {
                is Text -> Inline.Plain(node.literal)
                is StrongEmphasis -> Inline.Strong(inlines(node, depth + 1))
                is org.commonmark.node.Emphasis -> Inline.Emphasis(inlines(node, depth + 1))
                is SoftLineBreak -> Inline.Plain(" ")
                is HardLineBreak -> Inline.LineBreak
                is MarkdownLink -> Inline.Link(safeHref(node.destination), inlines(node, depth + 1))
                is Code, is org.commonmark.node.Image, is org.commonmark.node.HtmlInline -> throw InvalidPolicyTextException("inline_not_allowed")
                else -> throw InvalidPolicyTextException("inline_not_allowed")
            }
            node = node.next
        }
        return result
    }

    private fun safeHref(destination: String): String {
        val scheme = destination.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme !in allowedSchemes || destination.any { it.isISOControl() || it == ' ' }) {
            throw InvalidPolicyTextException("link_not_allowed")
        }
        return destination
    }
}
