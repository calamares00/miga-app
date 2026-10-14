package org.calamares.miga.data.ai

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the hint line changes while waiting for the model. */
private const val HINT_INTERVAL_MILLIS = 3500L

/**
 * What a long AI operation is doing right now: the current [step] ("Asking Google Gemini…"), an
 * optional [detail] line and the time it started, so the UI can show the elapsed seconds.
 */
data class AiProgress(val step: String, val detail: String? = null, val startedAt: Long)

/**
 * Reports the progress of an AI operation to the screen (and to the AI notification) through
 * [publish]. Steps are real stages of the operation; while the model works, which it does not
 * report, [withHints] cycles short descriptions of the task so the user sees it is still going.
 */
class AiProgressReporter(private val publish: (AiProgress) -> Unit) {
    private val startedAt = System.currentTimeMillis()

    @Volatile private var step: String = ""
    @Volatile private var detail: String? = null

    fun step(text: String) {
        step = text
        detail = null
        emit()
    }

    fun detail(text: String?) {
        detail = text
        emit()
    }

    /** Runs [block] while the detail line cycles through [hints]. */
    suspend fun <T> withHints(hints: List<String>, block: suspend () -> T): T {
        if (hints.isEmpty()) return block()
        return coroutineScope {
            val cycler = launch {
                var index = 0
                while (true) {
                    delay(HINT_INTERVAL_MILLIS)
                    detail(hints[index % hints.size])
                    index++
                }
            }
            try {
                block()
            } finally {
                cycler.cancel()
            }
        }
    }

    private fun emit() = publish(AiProgress(step, detail, startedAt))
}
