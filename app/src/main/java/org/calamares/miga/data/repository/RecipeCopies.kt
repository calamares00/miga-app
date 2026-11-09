package org.calamares.miga.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.toDraft
import org.calamares.miga.data.local.PhotoStorage

/**
 * Copies [recipe] into [targetBookId] as a new recipe with its own photo copies, so deleting one
 * never leaves the other without a photo. Also how a pack recipe is taken into one's own book.
 */
suspend fun RecipeRepository.copyRecipeToBook(context: Context, recipe: Recipe, targetBookId: Long): Long {
    val appContext = context.applicationContext
    val photos = withContext(Dispatchers.IO) {
        recipe.photos.mapNotNull { photo ->
            PhotoStorage.readBytes(photo.uri)
                ?.let { bytes -> PhotoStorage.copyBytesToInternalStorage(appContext, bytes) }
                ?.let { RecipePhoto(it, photo.isCover) }
        }
    }
    return saveRecipe(recipe.toDraft().copy(id = 0L, uid = null, recipeBookId = targetBookId, photos = photos))
}
