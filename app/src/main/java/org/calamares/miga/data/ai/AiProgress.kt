package org.calamares.miga.data.ai

/** A step of an AI operation that has ended: done, or failed (its text then says why). */
data class AiStepLine(val text: String, val failed: Boolean = false)

/**
 * What a long AI operation is doing right now: the current [step] ("Gemini is watching the
 * video") and when it started, the steps already finished ([done]), an optional [detail] line (a
 * countdown) and the time the whole operation started.
 */
data class AiProgress(
    val step: String,
    val detail: String? = null,
    val startedAt: Long,
    val done: List<AiStepLine> = emptyList(),
    val stepStartedAt: Long = startedAt
)

/**
 * Reports the progress of an AI operation to the screen (and to the AI notification) through
 * [publish]. Every step is a real stage of the operation: the model does not say what it is doing
 * while it works, so that wait is one step ("Gemini is reading the page", see [doing]) with its
 * own seconds, not invented hints.
 */
class AiProgressReporter(private val publish: (AiProgress) -> Unit) {
    private val startedAt = System.currentTimeMillis()

    @Volatile private var step: String = ""
    @Volatile private var stepStartedAt: Long = startedAt
    @Volatile private var detail: String? = null
    @Volatile private var done: List<AiStepLine> = emptyList()
    @Volatile private var doingText: ((provider: String) -> String)? = null

    /** A new step starts; the current one, if any, is done. */
    fun step(text: String) {
        if (step.isNotBlank() && step != text) done = done + AiStepLine(step)
        step = text
        stepStartedAt = System.currentTimeMillis()
        detail = null
        emit()
    }

    /** The current step turns out to be [text] (a provider that transcribes instead of listening). */
    fun replace(text: String) {
        step = text
        detail = null
        emit()
    }

    /** The current step failed because of [reason]; the next [step] follows it. */
    fun fail(reason: String) {
        if (step.isNotBlank()) done = done + AiStepLine("$step: $reason", failed = true)
        step = ""
        detail = null
        emit()
    }

    fun detail(text: String?) {
        detail = text
        emit()
    }

    /**
     * What the assistant does while [block] waits for it, by provider ("%1$s is reading the
     * page"): the step shown while each provider works (see runAi).
     */
    suspend fun <T> doing(text: (provider: String) -> String, block: suspend () -> T): T {
        val previous = doingText
        doingText = text
        return try {
            block()
        } finally {
            doingText = previous
        }
    }

    /** The step for [provider] working on the current task, or null when none was given. */
    fun doingFor(provider: String): String? = doingText?.invoke(provider)

    private fun emit() = publish(AiProgress(step, detail, startedAt, done, stepStartedAt))
}
