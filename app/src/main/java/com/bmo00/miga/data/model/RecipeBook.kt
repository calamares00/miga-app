package com.bmo00.miga.data.model

data class RecipeBook(
    val id: Long = 0L,
    val name: String,
    val coverPhotoUri: String?
)

data class RecipeBookSummary(
    val id: Long,
    val name: String,
    val coverPhotoUri: String?,
    val recipeCount: Int
)

data class RecipeBookDraft(
    val id: Long = 0L,
    val name: String,
    val coverPhotoUri: String?
)

fun RecipeBook.toDraft() = RecipeBookDraft(id = id, name = name, coverPhotoUri = coverPhotoUri)

fun emptyRecipeBookDraft() = RecipeBookDraft(id = 0L, name = "", coverPhotoUri = null)
