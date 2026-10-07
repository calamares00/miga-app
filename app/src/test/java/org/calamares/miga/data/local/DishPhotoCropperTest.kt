package org.calamares.miga.data.local

import org.calamares.miga.data.vision.DishPhotoDto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DishPhotoCropperTest {

    @Test
    fun `invalid boxes are discarded`() {
        val boxes = validDishBoxes(
            listOf(
                DishPhotoDto(image = 5, box = listOf(0, 0, 500, 500)), // missing image
                DishPhotoDto(image = 0, box = listOf(0, 0, 500)), // missing coordinates
                DishPhotoDto(image = 0, box = listOf(100, 100, 120, 120)), // tiny
                DishPhotoDto(image = 0, box = listOf(100, 200, 600, 900))
            ),
            imageCount = 1
        )
        assertEquals(1, boxes.size)
        assertEquals(0.1f, boxes[0].top, 0.001f)
        assertEquals(0.2f, boxes[0].left, 0.001f)
        assertEquals(0.6f, boxes[0].bottom, 0.001f)
        assertEquals(0.9f, boxes[0].right, 0.001f)
    }

    @Test
    fun `overlapping boxes on the same image are deduplicated`() {
        val boxes = validDishBoxes(
            listOf(
                DishPhotoDto(image = 0, box = listOf(100, 100, 600, 600)),
                DishPhotoDto(image = 0, box = listOf(110, 110, 610, 610)),
                DishPhotoDto(image = 1, box = listOf(100, 100, 600, 600))
            ),
            imageCount = 2
        )
        assertEquals(listOf(0, 1), boxes.map { it.image })
    }

    @Test
    fun `swapped coordinates are normalized`() {
        val box = validDishBoxes(listOf(DishPhotoDto(0, listOf(600, 900, 100, 200))), 1).single()
        assertTrue(box.top < box.bottom && box.left < box.right)
    }

    @Test
    fun `uniform margins are trimmed and textured content is kept`() {
        val w = 40
        val h = 40
        // Image with a 4-pixel plain white margin around "textured" content.
        val luminance = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x < 4 || y < 4 || x >= w - 4 || y >= h - 4) 250 else ((x * 37 + y * 91) % 200)
        }
        assertArrayEquals(intArrayOf(4, 4, 4, 4), uniformEdgeTrim(luminance, w, h))
    }

    @Test
    fun `trim never exceeds the maximum fraction`() {
        val luminance = IntArray(100 * 100) { 128 }
        assertArrayEquals(intArrayOf(15, 15, 15, 15), uniformEdgeTrim(luminance, 100, 100))
    }

    private val paper = 0xFFF0EDE6.toInt()
    private val ink = 0xFF222222.toInt()

    /** A 120x120 page with a colourful photo at [30, 90) x [40, 100), lines of text above and below. */
    private fun page(whitePlate: Boolean = false): IntArray = IntArray(120 * 120) { i ->
        val x = i % 120
        val y = i / 120
        val inPhoto = x in 30 until 90 && y in 40 until 100
        val inPlate = whitePlate && x in 50 until 72 && y in 58 until 82
        val inText = (y in 10..13 || y in 104..107) && x in 20 until 100 && x % 2 == 0
        when {
            inPlate -> paper
            inPhoto -> (0xFF shl 24) or (200 shl 16) or (((x * 5 + y * 3) % 120) shl 8) or 60
            inText -> ink
            else -> paper
        }
    }

    private fun assertInsidePhoto(rect: PixelRect) {
        assertTrue("$rect", rect.left >= 30 && rect.top >= 40 && rect.right <= 90 && rect.bottom <= 100)
        assertTrue("$rect covers too little of the photo", rect.area >= 60 * 60 * 85 / 100)
    }

    @Test
    fun `a loose AI box that takes in text is tightened to the photo`() {
        val rect = refinePhotoRect(page(), 120, 120, PixelRect(22, 8, 100, 110))!!
        assertInsidePhoto(rect)
    }

    @Test
    fun `an AI box that cuts the photo grows to cover it`() {
        val rect = refinePhotoRect(page(), 120, 120, PixelRect(40, 50, 80, 90))!!
        assertInsidePhoto(rect)
    }

    @Test
    fun `light areas inside the photo are kept`() {
        val rect = refinePhotoRect(page(whitePlate = true), 120, 120, PixelRect(28, 38, 92, 102))!!
        assertInsidePhoto(rect)
    }

    @Test
    fun `without a page around the box there is nothing to refine`() {
        val photo = IntArray(120 * 120) { i -> (0xFF shl 24) or ((i % 120) shl 16) or (90 shl 8) or 40 }
        assertNull(refinePhotoRect(photo, 120, 120, PixelRect(2, 2, 118, 118)))
    }

    @Test
    fun `largest rectangle of true cells`() {
        val cols = 5
        val rows = 4
        val cells = BooleanArray(cols * rows) { i -> val x = i % cols; val y = i / cols; !(x == 0 || (x == 4 && y == 0)) }
        assertEquals(PixelRect(1, 0, 4, 4), largestRectangle(cells, cols, rows))
    }

    @Test
    fun `refinements far from the AI box or tiny are rejected`() {
        val hint = PixelRect(10, 10, 110, 110)
        assertTrue(isPlausibleRefinement(PixelRect(20, 20, 100, 100), hint))
        assertFalse(isPlausibleRefinement(PixelRect(10, 10, 30, 30), hint))
        assertFalse(isPlausibleRefinement(PixelRect(0, 0, 60, 120), PixelRect(50, 0, 120, 120)))
    }

    @Test
    fun `rotation is rounded to a quarter turn`() {
        assertEquals(listOf(0, 90, 180, 270, 270, 90, 0), listOf(0, 90, 180, 270, -90, 85, 1000).map { normalizeRotation(it) })
        val box = validDishBoxes(listOf(DishPhotoDto(0, listOf(100, 100, 600, 600), rotation = 90)), 1).single()
        assertEquals(90, box.rotation)
    }
}
