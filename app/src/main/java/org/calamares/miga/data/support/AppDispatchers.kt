package org.calamares.miga.data.support

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers the app moves blocking and heavy work to. Code asks for them here instead of
 * naming [Dispatchers] directly, so a test can swap them for a test dispatcher and control when
 * that work runs.
 */
object AppDispatchers {
    /** Disk and network. */
    @Volatile
    var io: CoroutineDispatcher = Dispatchers.IO

    /** CPU-bound work: image processing, parsing, statistics. */
    @Volatile
    var default: CoroutineDispatcher = Dispatchers.Default

    /** Back to the real dispatchers, after a test replaced them. */
    fun reset() {
        io = Dispatchers.IO
        default = Dispatchers.Default
    }
}
