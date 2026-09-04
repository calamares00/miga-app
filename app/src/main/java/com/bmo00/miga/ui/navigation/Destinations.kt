package com.bmo00.miga.ui.navigation

object Destinations {
    const val BOOKS_ROUTE = "books"
    const val BOOK_ROUTE = "books/{bookId}"
    const val BOOK_EDITOR_ROUTE = "bookEditor?bookId={bookId}"
    const val DETAIL_ROUTE = "recipes/{recipeId}"
    const val EDITOR_ROUTE = "editor?recipeId={recipeId}&bookId={bookId}"
    const val SETTINGS_ROUTE = "settings"
    const val MANAGE_CATEGORIES_ROUTE = "settings/categories"
    const val MANAGE_UTENSILS_ROUTE = "settings/utensils"
    const val MANAGE_INGREDIENTS_ROUTE = "settings/ingredients"
    const val MANAGE_INGREDIENT_CATEGORIES_ROUTE = "settings/ingredientCategories"
    const val HELP_ROUTE = "help"

    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BOOK_ID = "bookId"
    const val NEW_RECIPE_ID = -1L
    const val NEW_BOOK_ID = -1L

    fun book(bookId: Long) = "books/$bookId"
    fun bookEditor(bookId: Long = NEW_BOOK_ID) = "bookEditor?bookId=$bookId"
    fun detail(recipeId: Long) = "recipes/$recipeId"
    fun editor(bookId: Long, recipeId: Long = NEW_RECIPE_ID) = "editor?recipeId=$recipeId&bookId=$bookId"
}
