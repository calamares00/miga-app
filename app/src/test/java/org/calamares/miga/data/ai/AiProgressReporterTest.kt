package org.calamares.miga.data.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The steps an AI operation shows: real ones, in order, with the failed ones saying why. */
class AiProgressReporterTest {

    private var last: AiProgress? = null
    private val reporter = AiProgressReporter { last = it }

    @Test
    fun `finished steps are kept in order and the current one is apart`() {
        reporter.step("Downloading the page…")
        reporter.step("The page has no recipe in the standard format")
        reporter.step("Gemini is reading the page")
        val progress = last!!
        assertEquals(listOf("Downloading the page…", "The page has no recipe in the standard format"), progress.done.map { it.text })
        assertEquals("Gemini is reading the page", progress.step)
        assertTrue(progress.done.none { it.failed })
    }

    @Test
    fun `a failed step says why and the next one follows it`() {
        reporter.step("Gemini is reading the page")
        reporter.fail("usage limit reached")
        reporter.step("Groq is reading the page")
        val progress = last!!
        assertEquals("Gemini is reading the page: usage limit reached", progress.done.single().text)
        assertTrue(progress.done.single().failed)
        assertEquals("Groq is reading the page", progress.step)
    }

    @Test
    fun `what the assistant does is given by provider only while it works`() = runBlocking {
        assertNull(reporter.doingFor("Gemini"))
        reporter.doing({ "$it is watching the video" }) {
            assertEquals("Gemini is watching the video", reporter.doingFor("Gemini"))
        }
        assertNull(reporter.doingFor("Gemini"))
    }

    @Test
    fun `a provider that transcribes replaces its step instead of finishing it`() {
        reporter.step("Groq is listening to the audio")
        reporter.replace("Transcribing the audio with Groq…")
        reporter.step("Groq is reading the transcript")
        assertEquals(listOf("Transcribing the audio with Groq…"), last!!.done.map { it.text })
    }

    @Test
    fun `each step has its own start time and the same step twice is not repeated`() {
        reporter.step("Downloading the photo…")
        val first = last!!.stepStartedAt
        Thread.sleep(5)
        reporter.step("Downloading the photo…")
        assertTrue(last!!.done.isEmpty())
        assertTrue(last!!.stepStartedAt >= first)
    }
}
