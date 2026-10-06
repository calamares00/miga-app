package org.calamares.miga.data.local

import org.calamares.miga.data.vision.DishPhotoDto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
