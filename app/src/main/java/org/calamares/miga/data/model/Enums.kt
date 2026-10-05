package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

enum class Difficulty(val label: String) {
    FACIL(L10n.str(R.string.facil)),
    MEDIA(L10n.str(R.string.media)),
    DIFICIL(L10n.str(R.string.dificil))
}

enum class SortOption(val label: String) {
    NAME_ASC(L10n.str(R.string.nombre_z)),
    RECENT(L10n.str(R.string.mas_reciente)),
    MOST_COOKED(L10n.str(R.string.mas_cocinada)),
    PREP_TIME(L10n.str(R.string.tiempo_preparacion)),
    BEST_RATED(L10n.str(R.string.mejor_valorada))
}
