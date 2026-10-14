package org.calamares.miga.data.ai

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an AI job runs or what it left: still running, or its result until it is seen. */
sealed interface AiJobState<out T> {
    data object Running : AiJobState<Nothing>
    data class Finished<T>(val result: T) : AiJobState<T>
}

/**
 * AI work whose result the user reviews (a recipe improved with AI), run outside of any screen
 * like the PDF exports: leaving the screen or the app does not lose it. While it runs the "AI is
 * working" notification shows (see runAi); when it ends the result is kept until the screen shows
 * it, and a notification that opens the recipe says it is ready, unless the user is watching it.
 * One job per key; cancelling stops the request at once (see postJson).
 *
 * Short work tied to what is on screen (a dialog, the photo editor, a form) stays in its screen's
 * scope instead and is cancelled when the user leaves, which also ends its notification.
 */
object AiJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private val watched = mutableSetOf<String>()
    private val _states = MutableStateFlow<Map<String, AiJobState<Any?>>>(emptyMap())

    @Suppress("UNCHECKED_CAST")
    fun <T> observe(key: String): Flow<AiJobState<T>?> =
        _states.map { it[key] as AiJobState<T>? }.distinctUntilChanged()

    fun isRunning(key: String): Boolean = jobs[key]?.isActive == true

    /**
     * Starts [block] under [key] unless it is already running. When it ends, [doneMessage] (null for
     * none) is notified and the notification opens [openRoute], where the result is shown.
     */
    fun <T> start(key: String, openRoute: String?, doneMessage: (T) -> String?, block: suspend () -> T) {
        if (isRunning(key)) return
        _states.update { it + (key to AiJobState.Running) }
        val job = scope.launch {
            try {
                val result = block()
                _states.update { it + (key to AiJobState.Finished(result)) }
                if (key !in watched) doneMessage(result)?.let { AiKeepAlive.announce(it, openRoute = openRoute) }
            } catch (e: CancellationException) {
                _states.update { it - key }
                throw e
            }
        }
        jobs[key] = job
        job.invokeOnCompletion { if (jobs[key] === job) jobs.remove(key) }
    }

    /** Stops the job and forgets it; its notification goes at once. */
    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
        _states.update { it - key }
    }

    /** Forgets a finished job's result once it has been seen (applied or discarded). */
    fun clear(key: String) {
        if (!isRunning(key)) _states.update { it - key }
    }

    /** While a screen shows the job, its end needs no notification. */
    fun watch(key: String, watching: Boolean) {
        if (watching) watched += key else watched -= key
    }

    // --- Opening the result from the "ready" notification ---

    const val EXTRA_OPEN_ROUTE = "org.calamares.miga.extra.OPEN_ROUTE"

    private val _openRoute = MutableStateFlow<String?>(null)

    /** A screen to open (navigation route), asked by a notification; the navigation consumes it. */
    val openRoute: StateFlow<String?> = _openRoute

    /**
     * Only the screens that show AI results: the launcher activity is exported, so another app
     * could send this extra to open any screen otherwise.
     */
    private val OPENABLE_ROUTES = listOf(Regex("""recipes/\d+(\?.*)?"""), Regex("""bulkPolish"""))

    fun handle(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_OPEN_ROUTE) ?: return
        if (OPENABLE_ROUTES.any { it.matches(route) }) _openRoute.value = route
    }

    fun consumeOpenRoute() {
        _openRoute.value = null
    }
}
