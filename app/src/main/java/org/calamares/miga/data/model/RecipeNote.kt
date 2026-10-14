package org.calamares.miga.data.model

/** A dated personal note on a recipe; see RecipeNoteEntity. */
data class RecipeNote(val id: Long, val text: String, val createdAt: Long)
