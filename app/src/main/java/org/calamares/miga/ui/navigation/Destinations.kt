package org.calamares.miga.ui.navigation

import android.net.Uri
import org.calamares.miga.data.model.ContentKind

object Destinations {
    const val BOOKS_ROUTE = "books"
    const val BOOK_ROUTE = "books/{bookId}"
    const val BOOK_EDITOR_ROUTE = "bookEditor?bookId={bookId}"
    const val DETAIL_ROUTE = "recipes/{recipeId}?browse={browse}"
    const val EDITOR_ROUTE = "editor?recipeId={recipeId}&bookId={bookId}&sourcePhotoUris={sourcePhotoUris}" +
        "&sourceDishName={sourceDishName}&sourceDishDescription={sourceDishDescription}&sourceDishOrigin={sourceDishOrigin}" +
        "&sourceRecipeUrl={sourceRecipeUrl}"
    const val SEARCH_ROUTE = "search"
    const val FAVORITES_ROUTE = "favorites"
    const val SHOPPING_LIST_ROUTE = "shoppingList"
    const val STATS_ROUTE = "stats"
    /** Search over the recipes behind a figure of the statistics; see SearchRequests. */
    const val FILTERED_SEARCH_ROUTE = "search/filtered"
    const val PACKS_CATALOG_ROUTE = "packs"
    const val PACK_DETAIL_ROUTE = "packs/{packId}"
    const val BULK_IMPORT_ROUTE = "bulkImport?bookId={bookId}&photoUris={photoUris}"
    const val DISH_SEARCH_ROUTE = "dishSearch?bookId={bookId}"
    const val IDEAS_ROUTE = "ideas"
    const val SETTINGS_ROUTE = "settings"
    const val SETTINGS_SECTION_ROUTE = "settings/section/{section}"
    /** One list of Settings > Manage content; {kind} is a ContentKind name. */
    const val MANAGE_CONTENT_ROUTE = "settings/content/{kind}"
    const val CONTENT_CLEANUP_ROUTE = "settings/cleanup"
    const val SYNC_CONNECTIONS_ROUTE = "settings/syncConnections"
    const val HELP_ROUTE = "help"
    const val HELP_CHANGELOG_ROUTE = "help/changelog"
    const val ABOUT_ROUTE = "about"
    const val PRIVACY_ROUTE = "privacy"

    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_SETTINGS_SECTION = "section"
    const val ARG_CONTENT_KIND = "kind"
    const val ARG_SOURCE_PHOTO_URIS = "sourcePhotoUris"
    const val ARG_SOURCE_DISH_NAME = "sourceDishName"
    const val ARG_SOURCE_DISH_DESCRIPTION = "sourceDishDescription"
    const val ARG_SOURCE_DISH_ORIGIN = "sourceDishOrigin"
    const val ARG_SOURCE_RECIPE_URL = "sourceRecipeUrl"
    const val ARG_PACK_ID = "packId"
    const val ARG_PHOTO_URIS = "photoUris"
    const val ARG_BROWSE_IDS = "browse"
    const val NEW_RECIPE_ID = -1L
    const val NEW_BOOK_ID = -1L

    fun book(bookId: Long) = "books/$bookId"
    fun packDetail(packId: String) = "packs/${Uri.encode(packId)}"
    fun bookEditor(bookId: Long = NEW_BOOK_ID) = "bookEditor?bookId=$bookId"
    /**
     * A recipe's detail. [browseIds] are the recipes the user can page through with a swipe, in the
     * order of the list the recipe was opened from (a book).
     */
    fun detail(recipeId: Long, browseIds: List<Long> = emptyList()) =
        if (browseIds.size > 1) "recipes/$recipeId?browse=${browseIds.joinToString(",")}" else "recipes/$recipeId"

    fun decodeIdList(raw: String?): List<Long> =
        raw?.split(",")?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
    fun settingsSection(sectionId: String) = "settings/section/$sectionId"

    fun editor(
        bookId: Long,
        recipeId: Long = NEW_RECIPE_ID,
        sourcePhotoUris: List<String> = emptyList(),
        sourceDishName: String? = null,
        sourceDishDescription: String? = null,
        sourceDishOrigin: String? = null,
        sourceRecipeUrl: String? = null
    ): String {
        val base = "editor?recipeId=$recipeId&bookId=$bookId"
        val withPhotos = if (sourcePhotoUris.isNotEmpty()) "$base&sourcePhotoUris=${encodeUriList(sourcePhotoUris)}" else base
        val withDish = if (sourceDishName != null) {
            "$withPhotos&sourceDishName=${Uri.encode(sourceDishName)}" +
                "&sourceDishDescription=${Uri.encode(sourceDishDescription.orEmpty())}" +
                "&sourceDishOrigin=${Uri.encode(sourceDishOrigin.orEmpty())}"
        } else {
            withPhotos
        }
        return if (sourceRecipeUrl != null) "$withDish&sourceRecipeUrl=${Uri.encode(sourceRecipeUrl)}" else withDish
    }
    fun bulkImport(bookId: Long, photoUris: List<String>): String =
        "bulkImport?bookId=$bookId&photoUris=${encodeUriList(photoUris)}"
    fun dishSearch(bookId: Long): String = "dishSearch?bookId=$bookId"

    /**
     * Navigation Compose has no clean list argument type for query args, so several URIs travel as
     * one String. Each URI goes through Uri.encode(), which escapes every literal comma as %2C, so
     * joining them with "," can never be confused with a URI's content.
     */
    private fun encodeUriList(uris: List<String>): String = uris.joinToString(",") { Uri.encode(it) }

    fun decodeUriList(raw: String?): List<String> =
        raw?.takeIf { it.isNotBlank() }?.split(",")?.map { Uri.decode(it) }.orEmpty()

    fun manageContent(kind: ContentKind) = "settings/content/${kind.name}"
}

