package com.bmo00.miga.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bmo00.miga.data.model.Ingredient
import com.bmo00.miga.data.model.IngredientGroup
import com.bmo00.miga.data.model.RecipePhoto
import com.bmo00.miga.data.model.StepGroup

class IngredientRowUi(name: String = "", quantity: String = "", unit: String = "") {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
    var unit by mutableStateOf(unit)
}

class IngredientGroupUi(name: String? = null, ingredients: List<IngredientRowUi> = emptyList()) {
    var name by mutableStateOf(name)
    val ingredients = mutableStateListOf<IngredientRowUi>().apply { addAll(ingredients) }
}

class StepRowUi(text: String = "") {
    var text by mutableStateOf(text)
}

class StepGroupUi(name: String? = null, steps: List<StepRowUi> = emptyList()) {
    var name by mutableStateOf(name)
    val steps = mutableStateListOf<StepRowUi>().apply { addAll(steps) }
}

class PhotoUi(uri: String, isCover: Boolean) {
    var uri by mutableStateOf(uri)
    var isCover by mutableStateOf(isCover)
}

fun IngredientGroup.toUi() = IngredientGroupUi(
    name = name,
    ingredients = ingredients.map { IngredientRowUi(it.name, it.quantity?.let { q -> formatEditorQuantity(q) } ?: "", it.unit.orEmpty()) }
)

fun StepGroup.toUi() = StepGroupUi(name = name, steps = instructions.map { StepRowUi(it) })

fun IngredientGroupUi.toDomain(): IngredientGroup = IngredientGroup(
    name = name,
    ingredients = ingredients
        .filter { it.name.isNotBlank() }
        .map { row ->
            Ingredient(
                name = row.name.trim(),
                quantity = row.quantity.trim().replace(',', '.').toDoubleOrNull(),
                unit = row.unit.trim().takeIf { it.isNotBlank() }
            )
        }
)

fun StepGroupUi.toDomain(): StepGroup = StepGroup(
    name = name,
    instructions = steps.map { it.text.trim() }.filter { it.isNotBlank() }
)

fun RecipePhoto.toUi() = PhotoUi(uri, isCover)
fun PhotoUi.toDomain() = RecipePhoto(uri, isCover)

private fun formatEditorQuantity(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
