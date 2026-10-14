package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

enum class ThemeMode(val label: String) {
    SYSTEM(L10n.str(R.string.system_default)),
    LIGHT(L10n.str(R.string.light)),
    DARK(L10n.str(R.string.dark))
}
