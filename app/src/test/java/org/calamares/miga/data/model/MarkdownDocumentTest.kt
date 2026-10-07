package org.calamares.miga.data.model

import org.calamares.miga.data.model.MarkdownDocument.Block
import org.calamares.miga.data.model.MarkdownDocument.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownDocumentTest {

    @Test
    fun `headings, wrapped paragraphs and lists`() {
        val blocks = MarkdownDocument.parse(
            """
            # Title

            First line
            continues here.

            - **One**: a bullet
              wrapped.
            - Two

            1. Item with sub-items:
               - nested
            
               Paragraph of the item.
            2. Next
            """.trimIndent()
        )
        assertEquals(
            listOf(
                Block(Kind.HEADING, "Title", level = 1),
                Block(Kind.PARAGRAPH, "First line continues here."),
                Block(Kind.BULLET, "**One**: a bullet wrapped."),
                Block(Kind.BULLET, "Two"),
                Block(Kind.NUMBERED, "Item with sub-items:", number = 1),
                Block(Kind.BULLET, "nested", level = 1),
                Block(Kind.PARAGRAPH, "Paragraph of the item.", level = 1),
                Block(Kind.NUMBERED, "Next", number = 2)
            ),
            blocks
        )
    }

    @Test
    fun `sections split at level two headings`() {
        val sections = MarkdownDocument.sections("## 📚 Books\n\nText.\n\n## ✨ Ideas {ai}\n\n- tip")
        assertEquals(listOf("📚 Books", "✨ Ideas {ai}"), sections.map { it.title })
        assertEquals(listOf(Block(Kind.BULLET, "tip")), sections[1].blocks)
    }

    private fun asset(name: String) = File("src/main/assets/docs/$name").readText()

    @Test
    fun `the privacy policy in the app is the one published in the repository`() {
        assertEquals(File("../PRIVACY.md").readText(), asset("privacy-es.md"))
    }

    @Test
    fun `both languages have the same documents and help topics`() {
        for (document in listOf("privacy", "help")) {
            val spanish = MarkdownDocument.sections(asset("$document-es.md"))
            val english = MarkdownDocument.sections(asset("$document-en.md"))
            assertEquals(document, spanish.size, english.size)
            assertEquals(document, spanish.map { it.title.endsWith("{ai}") }, english.map { it.title.endsWith("{ai}") })
        }
        assertTrue(MarkdownDocument.sections(asset("help-es.md")).all { it.title.isNotBlank() && it.blocks.isNotEmpty() })
    }
}
