package org.calamares.miga.data.vision

import org.calamares.miga.data.local.CandidateCrop
import org.calamares.miga.data.local.NormalizedBox
import org.calamares.miga.data.local.boxWithinRegion
import org.calamares.miga.data.local.candidateRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sorting photos into recipes, and confirming and reframing the photo of the dish. */
class PhotoImportTest {

    @Test
    fun `groups from the AI are completed and put in order`() {
        // Five photos: two pages of one recipe and three other recipes.
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3), listOf(4)), completeGroups(listOf(listOf(0, 1), listOf(2), listOf(3), listOf(4)), 5))
        // Left out, repeated or impossible numbers never lose or duplicate a photo.
        assertEquals(listOf(listOf(0), listOf(1, 3), listOf(2)), completeGroups(listOf(listOf(3, 1).sorted(), listOf(1, 9, -1), listOf(0)), 4))
        assertEquals(listOf(listOf(0), listOf(1)), completeGroups(emptyList(), 2))
    }

    @Test
    fun `the grouping prompt names every photo`() {
        val prompt = imageGroupingPrompt(5)
        assertTrue(prompt.contains("The 5 attached images"))
        assertTrue(prompt.contains("from 0 to 4"))
    }

    private fun box(top: Float, left: Float, bottom: Float, right: Float, image: Int = 0) = NormalizedBox(image, top, left, bottom, right)

    private fun candidate(box: NormalizedBox) = CandidateCrop(box, candidateRegion(box), ByteArray(0))

    @Test
    fun `a candidate region adds a margin and stays inside the image`() {
        val region = candidateRegion(box(0.4f, 0.4f, 0.6f, 0.6f))
        assertTrue(region.top < 0.4f && region.left < 0.4f && region.bottom > 0.6f && region.right > 0.6f)
        val corner = candidateRegion(box(0f, 0f, 0.5f, 1f))
        assertEquals(0f, corner.top)
        assertEquals(0f, corner.left)
        assertEquals(1f, corner.right)
    }

    @Test
    fun `a box inside a region maps back to the whole image`() {
        val region = box(0.2f, 0.4f, 0.6f, 0.8f)
        val mapped = boxWithinRegion(region, listOf(250, 500, 750, 1000), rotation = 90)!!
        assertEquals(0.3f, mapped.top, 0.001f)
        assertEquals(0.6f, mapped.left, 0.001f)
        assertEquals(0.5f, mapped.bottom, 0.001f)
        assertEquals(0.8f, mapped.right, 0.001f)
        assertEquals(90, mapped.rotation)
        assertNull(boxWithinRegion(region, emptyList(), 0))
        assertNull(boxWithinRegion(region, listOf(0, 0, 20, 20), 0))
    }

    @Test
    fun `only confirmed dishes are kept, the whole dish first`() {
        val text = candidate(box(0.05f, 0.05f, 0.30f, 0.90f))
        val detail = candidate(box(0.35f, 0.05f, 0.60f, 0.45f))
        val whole = candidate(box(0.65f, 0.10f, 0.95f, 0.90f))
        val boxes = confirmedDishBoxes(
            listOf(text, detail, whole),
            listOf(
                DishCheckDto(crop = 0, dish = false),
                DishCheckDto(crop = 1, dish = true, whole = false, box = listOf(100, 100, 900, 900)),
                DishCheckDto(crop = 2, dish = true, whole = true, box = listOf(150, 100, 850, 900), rotation = 0)
            )
        )
        assertEquals(2, boxes.size)
        // The whole dish is first (the cover), reframed inside its candidate; the text is gone.
        assertTrue(boxes[0].top > 0.6f)
        assertTrue(boxes[1].top in 0.3f..0.45f)
        assertTrue(boxes.none { it.top < 0.3f })
    }

    @Test
    fun `a candidate the AI did not answer about is dropped and an unusable box keeps the located one`() {
        val first = candidate(box(0.1f, 0.1f, 0.4f, 0.9f))
        val second = candidate(box(0.5f, 0.1f, 0.9f, 0.9f))
        val boxes = confirmedDishBoxes(listOf(first, second), listOf(DishCheckDto(crop = 1, dish = true, whole = true, box = emptyList())))
        assertEquals(listOf(second.box), boxes)
        assertTrue(confirmedDishBoxes(listOf(first), emptyList()).isEmpty())
    }

    @Test
    fun `the dish check prompt names the recipe and its candidates`() {
        val prompt = dishCheckPrompt("Tarta \"Tatin\"", 2)
        assertTrue(prompt.contains("Tarta 'Tatin'"))
        assertTrue(prompt.contains("The 2 attached image(s)"))
    }
}
