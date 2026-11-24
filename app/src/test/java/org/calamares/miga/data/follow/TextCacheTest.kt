package org.calamares.miga.data.follow

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the feeds work out from a post's text is done once per text. */
class TextCacheTest {

    @Test
    fun `a text is worked out once and the oldest ones are forgotten`() {
        val cache = TextCache<Int>(capacity = 2)
        var calls = 0
        val work = { calls++; 1 }
        cache.get("a", work)
        cache.get("a", work)
        assertEquals(1, calls)
        cache.get("b", work)
        cache.get("c", work)
        cache.get("a", work)
        assertEquals("a was forgotten after b and c", 4, calls)
    }
}
