package org.calamares.miga.ui.search

import org.calamares.miga.data.model.RecipeFilter

/** Recipes another screen asks to list in the search screen (a figure of the statistics). */
data class SearchRequest(val title: String, val filter: RecipeFilter)

/**
 * Hands a [SearchRequest] to the filtered search screen it opens: the filter holds sets and
 * enums that do not fit in a navigation route. Taken once by that screen's ViewModel, which then
 * keeps it across configuration changes.
 */
object SearchRequests {
    private var pending: SearchRequest? = null

    fun open(request: SearchRequest) {
        pending = request
    }

    fun take(): SearchRequest? = pending.also { pending = null }
}
