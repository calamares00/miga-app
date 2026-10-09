package org.calamares.miga.ui

import android.os.SystemClock

/**
 * Keeps the system splash screen until the first screen has its content, so it never appears
 * empty ("You have no books yet") for a moment before the data arrives.
 *
 * The first screen calls [contentReady] once its data has loaded; MainActivity keeps the splash
 * while [isWaiting]. A safety limit lets the app through even if that never happens.
 */
object StartupGate {
    private const val MAX_WAIT_MILLIS = 2000L

    private val startedAt = SystemClock.elapsedRealtime()

    @Volatile
    private var ready = false

    fun contentReady() {
        ready = true
    }

    val isWaiting: Boolean
        get() = !ready && SystemClock.elapsedRealtime() - startedAt < MAX_WAIT_MILLIS
}
