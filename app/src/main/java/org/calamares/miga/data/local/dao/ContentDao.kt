package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.calamares.miga.data.local.entity.CategoryEntity
import org.calamares.miga.data.local.entity.IngredientCatalogEntity
import org.calamares.miga.data.local.entity.IngredientCategoryEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.TagEntity
import org.calamares.miga.data.local.entity.UtensilEntity

/** A label of the user's content (category, equipment, tag...) and how many things use it. */
data class ContentRow(val id: Long, val name: String, val usage: Int)

/** A recipe and its category, to put it back when a cleanup is undone. */
data class RecipeCategoryLink(val id: Long, val categoryId: Long?)

/** A recipe whose book syncs, to queue it for upload after one of its labels changed. */
data class RecipeSyncTarget(val uid: String, val connectionId: Long)

/**
 * Settings > Manage content: the labels recipes use, with live usage counts, and the renames,
 * merges and deletions applied to them (see RecipeRepository's content management).
 */
@Dao
interface ContentDao {

    // --- Lists with their usage, updated whenever a recipe changes ---

    @Query("SELECT c.id, c.name, (SELECT COUNT(*) FROM recipes r WHERE r.categoryId = c.id) AS usage FROM categories c ORDER BY c.name COLLATE NOCASE")
    fun observeCategories(): Flow<List<ContentRow>>

    @Query("SELECT u.id, u.name, (SELECT COUNT(*) FROM recipe_utensil_cross_ref x WHERE x.utensilId = u.id) AS usage FROM utensils u ORDER BY u.name COLLATE NOCASE")
    fun observeUtensils(): Flow<List<ContentRow>>

    @Query("SELECT t.id, t.name, (SELECT COUNT(*) FROM recipe_tag_cross_ref x WHERE x.tagId = t.id) AS usage FROM tags t ORDER BY t.name COLLATE NOCASE")
    fun observeTags(): Flow<List<ContentRow>>

    /** Usage of an ingredient: the recipes with an ingredient of that exact name, in any case. */
    @Query(
        "SELECT c.id, c.name, (SELECT COUNT(DISTINCT i.recipeId) FROM ingredients i WHERE i.name = c.name COLLATE NOCASE) AS usage " +
            "FROM ingredient_catalog c ORDER BY c.name COLLATE NOCASE"
    )
    fun observeIngredients(): Flow<List<ContentRow>>

    /** Usage of an ingredient category: the ingredients filed under it. */
    @Query("SELECT g.id, g.name, (SELECT COUNT(*) FROM ingredient_catalog c WHERE c.categoryId = g.id) AS usage FROM ingredient_categories g ORDER BY g.name COLLATE NOCASE")
    fun observeIngredientCategories(): Flow<List<ContentRow>>

    // --- Another entry with the same name, in any case (a rename into it merges both) ---

    @Query("SELECT id FROM categories WHERE name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1")
    suspend fun categoryNamed(name: String, excludeId: Long): Long?

    @Query("SELECT id FROM utensils WHERE name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1")
    suspend fun utensilNamed(name: String, excludeId: Long): Long?

    @Query("SELECT id FROM tags WHERE name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1")
    suspend fun tagNamed(name: String, excludeId: Long): Long?

    @Query("SELECT id FROM ingredient_catalog WHERE name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1")
    suspend fun ingredientNamed(name: String, excludeId: Long): Long?

    @Query("SELECT id FROM ingredient_categories WHERE name = :name COLLATE NOCASE AND id != :excludeId LIMIT 1")
    suspend fun ingredientCategoryNamed(name: String, excludeId: Long): Long?

    // --- Names ---

    @Query("SELECT name FROM categories WHERE id = :id")
    suspend fun categoryName(id: Long): String?

    @Query("SELECT name FROM utensils WHERE id = :id")
    suspend fun utensilName(id: Long): String?

    @Query("SELECT name FROM tags WHERE id = :id")
    suspend fun tagName(id: Long): String?

    @Query("SELECT name FROM ingredient_catalog WHERE id = :id")
    suspend fun ingredientName(id: Long): String?

    @Query("SELECT name FROM ingredient_categories WHERE id = :id")
    suspend fun ingredientCategoryName(id: Long): String?

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE utensils SET name = :name WHERE id = :id")
    suspend fun renameUtensil(id: Long, name: String)

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    @Query("UPDATE ingredient_catalog SET name = :name WHERE id = :id")
    suspend fun renameIngredient(id: Long, name: String)

    @Query("UPDATE ingredient_categories SET name = :name WHERE id = :id")
    suspend fun renameIngredientCategory(id: Long, name: String)

    // --- Recipes using an entry ---

    @Query("SELECT id FROM recipes WHERE categoryId = :id")
    suspend fun recipesWithCategory(id: Long): List<Long>

    @Query("SELECT recipeId FROM recipe_utensil_cross_ref WHERE utensilId = :id")
    suspend fun recipesWithUtensil(id: Long): List<Long>

    @Query("SELECT recipeId FROM recipe_tag_cross_ref WHERE tagId = :id")
    suspend fun recipesWithTag(id: Long): List<Long>

    /** Recipes of the user's own books (not packs, which are not edited) with that ingredient. */
    @Query(
        "SELECT DISTINCT i.recipeId FROM ingredients i JOIN recipes r ON r.id = i.recipeId JOIN recipe_books b ON b.id = r.recipeBookId " +
            "WHERE i.name = :name COLLATE NOCASE AND b.packId IS NULL"
    )
    suspend fun ownRecipesWithIngredient(name: String): List<Long>

    // --- Moving recipes from one entry to another (merge) ---

    @Query("UPDATE recipes SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun moveCategory(fromId: Long, toId: Long)

    /** Recipes that already had both keep a single link: the rest of the old links go with the entry. */
    @Query("UPDATE OR IGNORE recipe_utensil_cross_ref SET utensilId = :toId WHERE utensilId = :fromId")
    suspend fun moveUtensil(fromId: Long, toId: Long)

    @Query("UPDATE OR IGNORE recipe_tag_cross_ref SET tagId = :toId WHERE tagId = :fromId")
    suspend fun moveTag(fromId: Long, toId: Long)

    @Query("UPDATE ingredient_catalog SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun moveIngredientCategory(fromId: Long, toId: Long)

    /** An ingredient merged into another passes on its category when the other has none. */
    @Query("UPDATE ingredient_catalog SET categoryId = (SELECT categoryId FROM ingredient_catalog WHERE id = :fromId) WHERE id = :toId AND categoryId IS NULL")
    suspend fun inheritIngredientCategory(fromId: Long, toId: Long)

    @Query(
        "UPDATE ingredients SET name = :newName WHERE name = :oldName COLLATE NOCASE AND recipeId IN " +
            "(SELECT r.id FROM recipes r JOIN recipe_books b ON b.id = r.recipeBookId WHERE b.packId IS NULL)"
    )
    suspend fun renameIngredientInOwnRecipes(oldName: String, newName: String)

    // --- Deletions (links cascade; recipes of a deleted category are left without one) ---

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM utensils WHERE id = :id")
    suspend fun deleteUtensil(id: Long)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("DELETE FROM ingredient_catalog WHERE id = :id")
    suspend fun deleteIngredient(id: Long)

    @Query("DELETE FROM ingredient_categories WHERE id = :id")
    suspend fun deleteIngredientCategory(id: Long)

    // --- Sync: recipes whose labels changed are uploaded again ---

    @Query("UPDATE recipes SET updatedAt = :now WHERE id IN (:ids)")
    suspend fun touchRecipes(ids: List<Long>, now: Long)

    @Query(
        "SELECT r.uid AS uid, b.syncConnectionId AS connectionId FROM recipes r JOIN recipe_books b ON b.id = r.recipeBookId " +
            "WHERE r.id IN (:ids) AND b.syncConnectionId IS NOT NULL"
    )
    suspend fun syncTargets(ids: List<Long>): List<RecipeSyncTarget>

    // --- Cleanup: what the shopping lists use, and a snapshot to undo it ---

    /** Names on the shopping lists and in the shopping history: ingredients in use there. */
    @Query("SELECT name FROM shopping_list_items WHERE deletedAt IS NULL UNION SELECT name FROM shopping_history")
    suspend fun shoppingNames(): List<String>

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("SELECT * FROM utensils")
    suspend fun allUtensils(): List<UtensilEntity>

    @Query("SELECT * FROM tags")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM ingredient_catalog")
    suspend fun allIngredients(): List<IngredientCatalogEntity>

    @Query("SELECT * FROM ingredient_categories")
    suspend fun allIngredientCategories(): List<IngredientCategoryEntity>

    @Query("SELECT * FROM recipe_utensil_cross_ref")
    suspend fun allUtensilLinks(): List<RecipeUtensilCrossRef>

    @Query("SELECT * FROM recipe_tag_cross_ref")
    suspend fun allTagLinks(): List<RecipeTagCrossRef>

    @Query("SELECT id, categoryId FROM recipes")
    suspend fun allRecipeCategories(): List<RecipeCategoryLink>

    @Query("DELETE FROM ingredient_catalog")
    suspend fun clearIngredients()

    @Query("DELETE FROM ingredient_categories")
    suspend fun clearIngredientCategories()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    /** Also removes their links to recipes (cascade); they are inserted again from the snapshot. */
    @Query("DELETE FROM utensils")
    suspend fun clearUtensils()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUtensils(items: List<UtensilEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(items: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(items: List<IngredientCatalogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredientCategories(items: List<IngredientCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUtensilLinks(items: List<RecipeUtensilCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagLinks(items: List<RecipeTagCrossRef>)

    @Query("UPDATE recipes SET categoryId = :categoryId WHERE id = :id")
    suspend fun setRecipeCategory(id: Long, categoryId: Long?)
}
