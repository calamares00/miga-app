package org.calamares.miga.data.ai

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.calamares.miga.MigaApp
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/** "Connect with OpenRouter" (OAuth PKCE): the authorisation link, the return link and the key exchange. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class OpenRouterConnectTest {

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private var server: HttpServer? = null

    @After
    fun stop() {
        server?.stop(0)
        OpenRouterConnect.clearStatus()
    }

    @Test
    fun `challenge follows RFC 7636`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OpenRouterConnect.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `the authorisation link carries the challenge, the callback page and a new state each time`() {
        val first = OpenRouterConnect.authorizationUri(app)
        val second = OpenRouterConnect.authorizationUri(app)
        assertEquals("openrouter.ai", first.host)
        assertEquals(OpenRouterConnect.CALLBACK_URL, first.getQueryParameter("callback_url"))
        assertEquals("S256", first.getQueryParameter("code_challenge_method"))
        assertEquals(43, first.getQueryParameter("code_challenge")!!.length)
        assertTrue(first.getQueryParameter("state") != second.getQueryParameter("state"))
    }

    @Test
    fun `only miga openrouter links are handled, and one with another state is refused`() {
        OpenRouterConnect.authorizationUri(app)
        assertFalse(OpenRouterConnect.handle(app, Intent(Intent.ACTION_VIEW, Uri.parse("miga://share?x=1"))))
        assertTrue(OpenRouterConnect.handle(app, Intent(Intent.ACTION_VIEW, Uri.parse("miga://openrouter?code=abc&state=other"))))
        assertTrue(OpenRouterConnect.status.value is OpenRouterConnect.Status.Failed)
    }

    private fun serve(code: Int, body: String): String {
        val received = StringBuilder()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/keys") { exchange ->
                received.append(exchange.requestBody.readBytes().decodeToString())
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        return "http://127.0.0.1:${server!!.address.port}/keys"
    }

    @Test
    fun `the code and verifier are exchanged for the key`() = runBlocking {
        val url = serve(200, """{"key":"sk-or-v1-test","user_id":"u"}""")
        assertEquals(OpenRouterConnect.Exchange.Key("sk-or-v1-test"), OpenRouterConnect.exchange("the-code", "the-verifier", url))
    }

    @Test
    fun `an expired code says so`() = runBlocking {
        val url = serve(403, """{"error":{"message":"Authorization code expired"}}""")
        assertTrue(OpenRouterConnect.exchange("old", "verifier", url) is OpenRouterConnect.Exchange.Error)
    }
}
