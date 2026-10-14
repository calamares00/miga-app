package org.calamares.miga.data.vision

import org.calamares.miga.data.ai.decodeAiJson
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoAdjustmentsTest {

    @Test
    fun `values out of range are brought back within it`() {
        val clamped = PhotoAdjustments(
            exposure = 5f,
            contrast = -3f,
            saturation = Float.NaN,
            warmth = -2f,
            shadows = -1f,
            highlights = 0.5f,
            sharpness = Float.POSITIVE_INFINITY
        ).clamped()
        assertEquals(1f, clamped.exposure)
        assertEquals(-0.5f, clamped.contrast)
        assertEquals(0f, clamped.saturation)
        assertEquals(-1f, clamped.warmth)
        assertEquals(0f, clamped.shadows)
        assertEquals(0f, clamped.highlights)
        assertEquals(0f, clamped.sharpness)
    }

    @Test
    fun `a model answer with text around it, missing fields and numbers as text is read`() {
        val answer = "Here you go:\n```json\n{\"exposure\": \"0.2\", \"contrast\": 0.15, \"sharpness\": 0.3}\n```"
        val adjustments = decodeAiJson(PhotoAdjustments.serializer(), answer).clamped()
        assertEquals(0.2f, adjustments.exposure, 0.0001f)
        assertEquals(0.15f, adjustments.contrast, 0.0001f)
        assertEquals(0.3f, adjustments.sharpness, 0.0001f)
        assertEquals(0f, adjustments.warmth)
    }
}
