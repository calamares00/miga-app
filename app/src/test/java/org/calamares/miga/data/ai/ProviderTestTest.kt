package org.calamares.miga.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetSocketAddress

/** "Test connection" against a local OpenAI-compatible server. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderTestTest {

    private var server: HttpServer? = null
    private val requests = mutableListOf<String>()

    @After
    fun stop() {
        server?.stop(0)
    }

    /** Answers text requests; requests with an image get [imageStatus]. */
    private fun serve(textStatus: Int, imageStatus: Int): String {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/v1/chat/completions") { exchange ->
                val body = exchange.requestBody.readBytes().decodeToString()
                requests += body
                val status = if ("image_url" in body) imageStatus else textStatus
                val answer = if (status == 200) """{"choices":[{"message":{"content":"OK"}}]}""" else """{"error":{"message":"nope"}}"""
                val bytes = answer.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        return "http://127.0.0.1:${server!!.address.port}/v1"
    }

    private fun candidate(url: String) = AiCandidate(AiProvider.CUSTOM, "", "local-model", url)

    @Test
    fun `text and photo both work`() = runBlocking {
        val result = ProviderTest.run(candidate(serve(200, 200)), checkImage = true)
        assertTrue(result.ok)
        assertEquals(true, result.readsImage)
        assertEquals(2, requests.size)
    }

    @Test
    fun `text works but the photo does not`() = runBlocking {
        val result = ProviderTest.run(candidate(serve(200, 400)), checkImage = true)
        assertTrue(result.ok)
        assertEquals(false, result.readsImage)
        assertTrue(!result.imageError.isNullOrBlank())
    }

    @Test
    fun `a rejected key stops before the photo`() = runBlocking {
        val result = ProviderTest.run(candidate(serve(401, 200)), checkImage = true)
        assertFalse(result.ok)
        assertNull(result.readsImage)
        assertEquals(1, requests.size)
    }

    @Test
    fun `no photo is sent when the model is set as not reading them`() = runBlocking {
        val result = ProviderTest.run(candidate(serve(200, 200)), checkImage = false)
        assertTrue(result.ok)
        assertNull(result.readsImage)
        assertEquals(1, requests.size)
    }
}
