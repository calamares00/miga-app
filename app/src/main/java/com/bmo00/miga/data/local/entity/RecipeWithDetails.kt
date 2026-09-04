package com.bmo00.miga.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,

    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity?,

    @Relation(parentColumn = "recipeBookId", entityColumn = "id")
    val recipeBook: RecipeBookEntity?,

    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,

    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,

    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val photos: List<RecipePhotoEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeTagCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeUtensilCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "utensilId"
        )
    )
    val utensils: List<UtensilEntity>
)
