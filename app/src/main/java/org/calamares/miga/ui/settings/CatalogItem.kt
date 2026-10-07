package org.calamares.miga.ui.settings

data class CatalogItem(
    val id: Long,
    val name: String,
    val usageCount: Int? = null,
    /** Created by the app on the first start rather than by the user. */
    val isDefault: Boolean = false
)
