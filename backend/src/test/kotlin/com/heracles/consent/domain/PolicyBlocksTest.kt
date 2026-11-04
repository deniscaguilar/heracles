package com.heracles.consent.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** E2-S4.1: only paragraphs, headings and lists of text, emphasis and safe links can exist. */
class PolicyBlocksTest {
    @Test
    fun `paragraphs, headings, lists, emphasis and https links become blocks`() {
        val blocks = PolicyBlocks.parse(
            """
            # Terms

            You agree to **this** and *that*, see [the site](https://example.org/terms).

            - first
            - second

            1. one
            2. two
            """.trimIndent(),
        )
        assertThat(blocks).hasSize(4)
        assertThat(blocks[0]).isEqualTo(Block.Heading(1, listOf(Inline.Plain("Terms"))))
        val paragraph = blocks[1] as Block.Paragraph
        assertThat(paragraph.children).contains(Inline.Strong(listOf(Inline.Plain("this"))))
        assertThat(paragraph.children).anyMatch { it is Inline.Link && it.href == "https://example.org/terms" }
        assertThat((blocks[2] as Block.ListBlock).ordered).isFalse()
        assertThat((blocks[2] as Block.ListBlock).items).hasSize(2)
        assertThat((blocks[3] as Block.ListBlock).ordered).isTrue()
    }

    @Test
    fun `a mailto link is allowed`() {
        val block = PolicyBlocks.parse("Write to [us](mailto:privacy@example.org).").single() as Block.Paragraph
        assertThat(block.children).anyMatch { it is Inline.Link && it.href == "mailto:privacy@example.org" }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "<script>alert(1)</script>",
            "<div>raw html</div>",
            "text with <b>inline html</b>",
            "![an image](https://example.org/a.png)",
            "```\ncode\n```",
            "    indented code",
            "`inline code`",
            "> a quote",
            "---",
            "- item\n  - nested",
            "- first paragraph\n\n  second paragraph",
        ],
    )
    fun `anything outside the format is refused`(markdown: String) {
        assertThatThrownBy { PolicyBlocks.parse(markdown) }.isInstanceOf(InvalidPolicyTextException::class.java)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "[x](javascript:alert(1))",
            "[x](JavaScript:alert(1))",
            "[x](data:text/html;base64,AAAA)",
            "[x](http://example.org)",
            "[x](/relative/path)",
            "[x](//example.org)",
            "[x](vbscript:msgbox)",
        ],
    )
    fun `links other than https and mailto are refused`(markdown: String) {
        assertThatThrownBy { PolicyBlocks.parse(markdown) }
            .isInstanceOf(InvalidPolicyTextException::class.java)
            .hasMessage("link_not_allowed")
    }

    @Test
    fun `a table is just text, since the table extension is not enabled`() {
        val blocks = PolicyBlocks.parse("| a | b |\n|---|---|\n| 1 | 2 |")
        assertThat(blocks.single()).isInstanceOf(Block.Paragraph::class.java)
    }

    @Test
    fun `the refusal never repeats the text`() {
        val secret = "my-secret-phrase"
        assertThatThrownBy { PolicyBlocks.parse("<div>$secret</div>") }.message().doesNotContain(secret)
    }
}
