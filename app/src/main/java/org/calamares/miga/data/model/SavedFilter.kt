package org.calamares.miga.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A search the user saved under a name: the text and the filter sheet's conditions (not the
 * statistics ones, which come and go with that screen).
 */
@Serializable
data class SavedFilter(
    val name: String,
    val query: String = "",
    val categoryNames: Set<String> = emptySet(),
    val difficulties: Set<String> = emptySet(),
    val utensils: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val ingredients: Set<String> = emptySet(),
    val origins: Set<String> = emptySet(),
    val onlyFavorites: Boolean = false,
    val sortOption: String = SortOption.NAME_ASC.name
) {
    fun toFilter(): RecipeFilter = RecipeFilter(
        query = query,
        categoryNames = categoryNames,
        difficulties = difficulties.mapNotNull { name -> Difficulty.entries.firstOrNull { it.name == name } }.toSet(),
        utensils = utensils,
        tags = tags,
        ingredients = ingredients,
        origins = origins,
        onlyFavorites = onlyFavorites,
        sortOption = SortOption.entries.firstOrNull { it.name == sortOption } ?: SortOption.NAME_ASC
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(serializer())

        fun from(name: String, filter: RecipeFilter) = SavedFilter(
            name = name.trim(),
            query = filter.query.trim(),
            categoryNames = filter.categoryNames,
            difficulties = filter.difficulties.map { it.name }.toSet(),
            utensils = filter.utensils,
            tags = filter.tags,
            ingredients = filter.ingredients,
            origins = filter.origins,
            onlyFavorites = filter.onlyFavorites,
            sortOption = filter.sortOption.name
        )

        fun encode(filters: List<SavedFilter>): String = json.encodeToString(listSerializer, filters)

        fun decode(text: String?): List<SavedFilter> =
            text?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
    }
}
