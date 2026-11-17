package org.calamares.miga.data.ai

/**
 * Where each provider creates API keys, and what its keys look like. Used by "Get a key": Settings
 * opens the page and, back in Miga, offers a key of that provider found in the clipboard. The
 * formats are only a check that the copied text is plausibly that provider's key, never shown or
 * sent anywhere.
 */
object ApiKeyPages {

    fun pageFor(provider: AiProvider): String? = when (provider) {
        AiProvider.GEMINI -> "https://aistudio.google.com/apikey"
        AiProvider.ANTHROPIC -> "https://platform.claude.com/settings/keys"
        AiProvider.OPENROUTER -> "https://openrouter.ai/settings/keys"
        AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
        AiProvider.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
        AiProvider.MISTRAL -> "https://console.mistral.ai/api-keys"
        AiProvider.GROQ -> "https://console.groq.com/keys"
        AiProvider.CUSTOM -> null
    }

    private val GEMINI = Regex("""AIza[0-9A-Za-z_-]{35}""")
    private val ANTHROPIC = Regex("""sk-ant-[0-9A-Za-z_-]{20,}""")
    private val OPENROUTER = Regex("""sk-or-v1-[0-9a-f]{64}""")
    private val GROQ = Regex("""gsk_[0-9A-Za-z]{40,}""")
    private val DEEPSEEK = Regex("""sk-[0-9a-f]{32}""")
    /** Project, service account and admin keys; older keys are "sk-" and 48 letters or digits. */
    private val OPENAI = Regex("""sk-(?:proj-|svcacct-|admin-)[0-9A-Za-z_-]{20,}|sk-[0-9A-Za-z]{48}""")
    private val MISTRAL = Regex("""[0-9A-Za-z]{32}""")

    /** Whether [text] (copied text, trimmed) looks like a key of [provider]. */
    fun looksLike(provider: AiProvider, text: String): Boolean {
        val key = text.trim()
        if (key.isEmpty() || key.length > 300 || key.any { it.isWhitespace() }) return false
        val format = when (provider) {
            AiProvider.GEMINI -> GEMINI
            AiProvider.ANTHROPIC -> ANTHROPIC
            AiProvider.OPENROUTER -> OPENROUTER
            AiProvider.OPENAI -> OPENAI
            AiProvider.DEEPSEEK -> DEEPSEEK
            AiProvider.MISTRAL -> MISTRAL
            AiProvider.GROQ -> GROQ
            AiProvider.CUSTOM -> return false
        }
        return format.matches(key)
    }
}
