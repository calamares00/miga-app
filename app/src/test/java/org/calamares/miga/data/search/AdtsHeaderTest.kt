package org.calamares.miga.data.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ADTS headers for the AAC tracks of videos (see PostAudioExtractor). */
class AdtsHeaderTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `aac lc at 44100 hz stereo`() {
        // AudioSpecificConfig 0x12 0x10: object type 2, frequency index 4, 2 channels.
        val header = AdtsHeader.from(bytes(0x12, 0x10))!!
        assertArrayEquals(bytes(0xFF, 0xF1, 0x50, 0x80, 0x0D, 0x7F, 0xFC), header.forFrame(100))
    }

    @Test
    fun `he-aac as instagram sends it goes as its lc core`() {
        // 0x2B 0x92 0x08 0x00 (a real reel): SBR, core at 22050 Hz (index 7), 2 channels,
        // extension at 44100 Hz. Checked with ffmpeg: the stream decodes as HE-AAC at 44100 Hz.
        val header = AdtsHeader.from(bytes(0x2B, 0x92, 0x08, 0x00))!!
        assertArrayEquals(bytes(0xFF, 0xF1, 0x5C, 0x80, 0x0D, 0x7F, 0xFC), header.forFrame(100))
    }

    @Test
    fun `frame lengths over 2047 bytes use the high bits`() {
        val header = AdtsHeader.from(bytes(0x12, 0x10))!!
        // 3000 + 7 = 3007 bytes: 13 bits split over three bytes.
        assertArrayEquals(bytes(0xFF, 0xF1, 0x50, 0x81, 0x77, 0xFF, 0xFC), header.forFrame(3000))
    }

    @Test
    fun `what adts cannot carry`() {
        assertNull(AdtsHeader.from(null))
        assertNull(AdtsHeader.from(bytes(0x12)))
        // Frequency index 15 (explicit frequency).
        assertNull(AdtsHeader.from(bytes(0x17, 0x90)))
        // Object type 31 (escape).
        assertNull(AdtsHeader.from(bytes(0xF8, 0x10)))
    }
}
