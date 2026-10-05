package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

enum class RecipeListViewMode(val label: String) {
    COMPACT(L10n.str(R.string.compacta)),
    NORMAL(L10n.str(R.string.normal)),
    GRID(L10n.str(R.string.cuadricula))
}
