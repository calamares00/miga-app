package org.calamares.miga.data.ai

import android.widget.Toast
import android.os.Looper
import android.os.Handler
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.support.AppDispatchers
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * "Connect with OpenRouter": instead of copying a key, the user logs in to OpenRouter in the
 * browser and authorises Miga, which gets a key of its own (OAuth PKCE, see
 * https://openrouter.ai/docs/use-cases/oauth-pkce).
 *
 * OpenRouter only redirects to https or localhost, so its callback is a page of the Miga website
 * that hands the code back to the app as `miga://openrouter?code=...&state=...` (see [handle]),
 * and also shows it to paste by hand ([connectWithCode]). The code is worthless without the
 * verifier, which never leaves the app. Verifier and state survive the app being closed while the
 * browser is open.
 */
object OpenRouterConnect {
    const val CALLBACK_URL = "https://miga.calamares.org/openrouter/"
    private const val AUTH_URL = "https://openrouter.ai/auth"
    private const val KEYS_URL = "https://openrouter.ai/api/v1/auth/keys"
    private const val PREFS = "openrouter_connect"
    private const val KEY_VERIFIER = "verifier"
    private const val KEY_STATE = "state"
    private const val TIMEOUT_MILLIS = 30_000

    sealed interface Status {
        data object Idle : Status
        data object Connecting : Status
        data object Connected : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val random = SecureRandom()

    /** Page where the user authorises Miga; a new verifier and state are kept for the answer. */
    fun authorizationUri(context: Context): Uri {
        val verifier = randomToken(48)
        val state = randomToken(16)
        prefs(context).edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        _status.value = Status.Idle
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("callback_url", CALLBACK_URL)
            .appendQueryParameter("code_challenge", codeChallenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("key_label", "Miga")
            .appendQueryParameter("state", state)
            .build()
    }

    /** Handles `miga://openrouter?code=...`; true when [intent] was that link. */
    fun handle(context: Context, intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != "miga" || data.host != "openrouter") return false
        val code = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        val expectedState = prefs(context).getString(KEY_STATE, null)
        when {
            code.isNullOrBlank() -> _status.value = Status.Failed(L10n.str(R.string.openrouter_connect_denied))
            // A link this app did not start (or an old one): not used.
            state != null && state != expectedState -> _status.value = Status.Failed(L10n.str(R.string.openrouter_connect_expired))
            else -> connectWithCode(context, code)
        }
        return true
    }

    /** Exchanges a code (from the link or pasted by the user) for a key and saves it. */
    fun connectWithCode(context: Context, code: String) {
        val app = context.applicationContext
        val verifier = prefs(app).getString(KEY_VERIFIER, null)
        if (verifier == null) {
            _status.value = Status.Failed(L10n.str(R.string.openrouter_connect_expired))
            return
        }
        _status.value = Status.Connecting
        scope.launch {
            val status = when (val result = exchange(code.trim(), verifier)) {
                is Exchange.Key -> {
                    val settings = (app as MigaApp).settingsRepository
                    settings.setApiKey(AiProvider.OPENROUTER, result.key)
                    settings.setProviderEnabled(AiProvider.OPENROUTER, true)
                    prefs(app).edit().clear().apply()
                    Status.Connected
                }
                is Exchange.Error -> Status.Failed(result.reason)
            }
            _status.value = status
            // The app may have opened on another screen: say how it went wherever the user is.
            val message = if (status is Status.Failed) status.reason else L10n.str(R.string.openrouter_connected)
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    fun clearStatus() {
        _status.value = Status.Idle
    }

    internal sealed interface Exchange {
        data class Key(val key: String) : Exchange
        data class Error(val reason: String) : Exchange
    }

    internal suspend fun exchange(code: String, verifier: String, url: String = KEYS_URL): Exchange {
        val body = buildJsonObject {
            put("code", code)
            put("code_verifier", verifier)
            put("code_challenge_method", "S256")
        }.toString()
        val response = try {
            postJson(url, body, emptyMap(), TIMEOUT_MILLIS)
        } catch (e: IOException) {
            return Exchange.Error(L10n.str(R.string.openrouter_connect_network))
        }
        if (response.code == 403 || response.code == 400) return Exchange.Error(L10n.str(R.string.openrouter_connect_expired))
        if (!response.isSuccessful) return Exchange.Error(L10n.str(R.string.openrouter_connect_failed_x, response.code))
        val key = runCatching { aiJson.parseToJsonElement(response.body).jsonObject["key"]?.jsonPrimitive?.content }.getOrNull()
        return if (key.isNullOrBlank()) Exchange.Error(L10n.str(R.string.openrouter_connect_failed_x, response.code)) else Exchange.Key(key)
    }

    /** S256 code challenge: base64url (no padding) of the SHA-256 of [verifier]. */
    internal fun codeChallenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun randomToken(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also { random.nextBytes(it) })

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
