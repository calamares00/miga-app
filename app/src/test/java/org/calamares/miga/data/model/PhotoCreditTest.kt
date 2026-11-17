package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoCreditTest {
    private val notes = "Encárgalo abierto en canal.\n\n📷 Foto: [Tamorlan](https://commons.wikimedia.org/wiki/File:X.jpg) · CC BY 3.0"

    @Test
    fun `the credit is split from the notes, shown plain and kept when editing`() {
        val (text, credit) = PhotoCredit.split(notes)
        assertEquals("Encárgalo abierto en canal.", text)
        assertEquals("📷 Foto: Tamorlan · CC BY 3.0", PhotoCredit.plain(credit!!))
        assertEquals(notes.replace("canal.", "canal. Para 6."), PhotoCredit.join("Encárgalo abierto en canal. Para 6.", credit))
        assertEquals(credit, PhotoCredit.join("", credit))
    }

    @Test
    fun `notes without a credit stay as they are`() {
        assertEquals("Sin foto" to null, PhotoCredit.split("Sin foto"))
        assertNull(PhotoCredit.split("").second)
    }
}
