package org.calamares.miga.data.ai

import org.calamares.miga.data.support.ErrorDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The wait a provider asks for after a rate limit is read from its error. */
class RateLimitsTest {

    private fun error(detail: String) = ErrorDetail.markAsAi(ErrorDetail.withDetail("Limit reached", detail))

    @Test
    fun `groq states the wait in seconds with decimals`() {
        val detail = "HTTP 429\nRate limit reached for model `qwen/qwen3.8-27b` in organization `org_x` service tier `on_demand` on " +
            "output tokens per minute (OTPM): Limit 1000, Used 935, Requested 931. Please try again in 51.96s. Need more tokens?"
        assertEquals(51_960L, RateLimits.waitMillis(error(detail)))
    }

    @Test
    fun `openai and gemini forms and the Retry-After header`() {
        assertEquals(80_000L, RateLimits.waitMillis(error("HTTP 429\nRate limit reached. Please try again in 1m20s.")))
        assertEquals(450L, RateLimits.waitMillis(error("HTTP 429\nPlease try again in 450ms.")))
        assertEquals(31_200L, RateLimits.waitMillis(error("HTTP 429\nQuota exceeded. Please retry in 31.2s.")))
        assertEquals(20_000L, RateLimits.waitMillis(error("HTTP 429\nRetry-After: 20\nToo many requests")))
        assertEquals(15_000L, RateLimits.waitMillis(error("HTTP 429\nPlease retry after 15 seconds")))
    }

    @Test
    fun `no wait for other errors or a limit without a time`() {
        assertNull(RateLimits.waitMillis(error("HTTP 401\nInvalid key. Please try again in 5s.")))
        assertNull(RateLimits.waitMillis(error("HTTP 429\nYou exceeded your current quota.")))
        assertNull(RateLimits.waitMillis("plain error"))
    }
}
