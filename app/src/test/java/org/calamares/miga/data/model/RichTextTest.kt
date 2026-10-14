package org.calamares.miga.data.model

import org.calamares.miga.data.model.RichText.LineKind
import org.calamares.miga.data.model.RichText.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    private fun runs(text: String) = RichText.blocks(text).single().runs

    @Test
    fun `bold italic and strikethrough`() {
        assertEquals(
            listOf(Run("Bate "), Run("muy", bold = true), Run(" bien "), Run("sin", italic = true), Run(" "), Run("prisa", strike = true)),
            runs("Bate **muy** bien *sin* ~~prisa~~")
        )
    }

    @Test
    fun `underscore variants`() {
        assertEquals(listOf(Run("a", bold = true), Run(" "), Run("b", italic = true)), runs("__a__ _b_"))
    }

    @Test
    fun `nested bold and italic`() {
        assertEquals(listOf(Run("todo", bold = true, italic = true)), runs("***todo***"))
        assertEquals(
            listOf(Run("muy ", italic = true), Run("bien", bold = true, italic = true), Run(" hecho", italic = true)),
            runs("*muy **bien** hecho*")
        )
    }

    @Test
    fun `lone or spaced asterisks are literal`() {
        assertEquals(listOf(Run("2 * 3 * 4")), runs("2 * 3 * 4"))
        assertEquals(listOf(Run("**sin cerrar")), runs("**sin cerrar"))
        assertEquals(listOf(Run("snake_case_name")), runs("snake_case_name"))
    }

    @Test
    fun `escapes show the character`() {
        assertEquals(listOf(Run("*no*")), runs("\\*no\\*"))
    }

    @Test
    fun `lists and headings`() {
        val blocks = RichText.blocks("# Masa\n- harina\n* agua\n1. Mezclar\n2) Amasar\nfin")
        assertEquals(
            listOf(LineKind.HEADING, LineKind.BULLET, LineKind.BULLET, LineKind.NUMBERED, LineKind.NUMBERED, LineKind.PARAGRAPH),
            blocks.map { it.kind }
        )
        assertEquals(listOf("Masa", "harina", "agua", "Mezclar", "Amasar", "fin"), blocks.map { it.plainText })
        assertEquals(2, blocks[4].number)
        assertTrue(blocks[0].runs.all { it.bold })
    }

    @Test
    fun `plain text for sharing and speech`() {
        assertEquals("Notas\n• uno\n• dos\n1. paso", RichText.toPlainText("**Notas**\n- uno\n- *dos*\n1. paso"))
        assertEquals("Texto normal", RichText.toPlainText("Texto normal"))
        assertFalse(RichText.hasFormatting("Texto normal, 2 * 3"))
        assertTrue(RichText.hasFormatting("Texto **normal**"))
    }

    @Test
    fun `toggle bold wraps and unwraps the selection`() {
        val wrapped = RichTextEditing.toggleWrap("Bate bien", 5, 9, "**")
        assertEquals("Bate **bien**", wrapped.text)
        assertEquals(7, wrapped.selectionStart)
        assertEquals(11, wrapped.selectionEnd)
        assertEquals("Bate bien", RichTextEditing.toggleWrap(wrapped.text, wrapped.selectionStart, wrapped.selectionEnd, "**").text)
    }

    @Test
    fun `toggle italic on bold text adds a third asterisk`() {
        val edit = RichTextEditing.toggleWrap("**bien**", 2, 6, "*")
        assertEquals("***bien***", edit.text)
        assertEquals("**bien**", RichTextEditing.toggleWrap(edit.text, edit.selectionStart, edit.selectionEnd, "*").text)
    }

    @Test
    fun `toggle with no selection inserts an empty pair`() {
        val edit = RichTextEditing.toggleWrap("Bate ", 5, 5, "**")
        assertEquals("Bate ****", edit.text)
        assertEquals(7, edit.selectionStart)
    }

    @Test
    fun `selection spaces stay outside the markers`() {
        assertEquals("Bate **bien** ya", RichTextEditing.toggleWrap("Bate bien ya", 4, 10, "**").text)
    }

    @Test
    fun `toggle list on several lines`() {
        val bullets = RichTextEditing.toggleList("uno\ndos\ntres", 0, 7, numbered = false)
        assertEquals("- uno\n- dos\ntres", bullets.text)
        val numbered = RichTextEditing.toggleList(bullets.text, 0, 3, numbered = true)
        assertEquals("1. uno\n- dos\ntres", numbered.text)
        assertEquals("uno\n- dos\ntres", RichTextEditing.toggleList(numbered.text, 0, 0, numbered = true).text)
    }

    @Test
    fun `enter continues and ends lists`() {
        val next = RichTextEditing.continueList("- harina\n", 9)!!
        assertEquals("- harina\n- ", next.text)
        assertEquals(11, next.selectionStart)
        assertEquals("1. Mezclar\n2. ", RichTextEditing.continueList("1. Mezclar\n", 11)!!.text)
        val ended = RichTextEditing.continueList("- harina\n- \n", 12)!!
        assertEquals("- harina\n", ended.text)
        assertEquals(9, ended.selectionStart)
        assertNull(RichTextEditing.continueList("texto\n", 6))
    }
}
