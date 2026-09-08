package com.bmo00.miga.ui.navigation

import android.net.Uri

object Destinations {
    const val BOOKS_ROUTE = "books"
    const val BOOK_ROUTE = "books/{bookId}"
    const val BOOK_EDITOR_ROUTE = "bookEditor?bookId={bookId}"
    const val DETAIL_ROUTE = "recipes/{recipeId}"
    const val EDITOR_ROUTE = "editor?recipeId={recipeId}&bookId={bookId}&sourcePhotoUris={sourcePhotoUris}"
    const val SEARCH_ROUTE = "search"
    const val FAVORITES_ROUTE = "favorites"
    const val SHOPPING_LIST_ROUTE = "shoppingList"
    const val PACKS_CATALOG_ROUTE = "packs"
    const val PACK_DETAIL_ROUTE = "packs/{packId}"
    const val BULK_IMPORT_ROUTE = "bulkImport?bookId={bookId}&photoUris={photoUris}"
    const val SETTINGS_ROUTE = "settings"
    const val MANAGE_CATEGORIES_ROUTE = "settings/categories"
    const val MANAGE_UTENSILS_ROUTE = "settings/utensils"
    const val MANAGE_INGREDIENTS_ROUTE = "settings/ingredients"
    const val MANAGE_INGREDIENT_CATEGORIES_ROUTE = "settings/ingredientCategories"
    const val SYNC_CONNECTIONS_ROUTE = "settings/syncConnections"
    const val HELP_ROUTE = "help"
    const val ABOUT_ROUTE = "about"

    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_SOURCE_PHOTO_URIS = "sourcePhotoUris"
    const val ARG_PACK_ID = "packId"
    const val ARG_PHOTO_URIS = "photoUris"
    const val NEW_RECIPE_ID = -1L
    const val NEW_BOOK_ID = -1L

    fun book(bookId: Long) = "books/$bookId"
    fun packDetail(packId: String) = "packs/${Uri.encode(packId)}"
    fun bookEditor(bookId: Long = NEW_BOOK_ID) = "bookEditor?bookId=$bookId"
    fun detail(recipeId: Long) = "recipes/$recipeId"
    fun editor(bookId: Long, recipeId: Long = NEW_RECIPE_ID, sourcePhotoUris: List<String> = emptyList()): String {
        val base = "editor?recipeId=$recipeId&bookId=$bookId"
        return if (sourcePhotoUris.isNotEmpty()) "$base&sourcePhotoUris=${encodeUriList(sourcePhotoUris)}" else base
    }
    fun bulkImport(bookId: Long, photoUris: List<String>): String =
        "bulkImport?bookId=$bookId&photoUris=${encodeUriList(photoUris)}"

    // Navigation Compose no tiene un tipo de argumento de lista limpio para rutas con query args,
    // así que varias URIs se codifican como una sola String: cada URI ya pasa por Uri.encode()
    // (que escapa toda coma literal a %2C), así que unirlas con "," como delimitador es seguro y
    // nunca puede confundirse con el contenido de una URI real.
    private fun encodeUriList(uris: List<String>): String = uris.joinToString(",") { Uri.encode(it) }

    fun decodeUriList(raw: String?): List<String> =
        raw?.takeIf { it.isNotBlank() }?.split(",")?.map { Uri.decode(it) }.orEmpty()
}
