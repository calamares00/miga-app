package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

enum class ThemeMode(val label: String) {
    SYSTEM(L10n.str(R.string.definido_sistema)),
    LIGHT(L10n.str(R.string.claro)),
    DARK(L10n.str(R.string.oscuro))
}
