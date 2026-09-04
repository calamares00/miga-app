package org.calamares.miga.ui.settings

data class CatalogItem(
    val id: Long,
    val name: String,
    val usageCount: Int? = null
)
