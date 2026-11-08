package org.calamares.miga.ui.packs

import java.text.NumberFormat
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.remote.PackInstallStats

/** "1 install" / "1,234 installs" for the catalogue, or null while a pack has none (or no counter). */
fun installsLabel(stats: PackInstallStats?): String? {
    val installs = stats?.installs ?: return null
    if (installs <= 0) return null
    return if (installs == 1) L10n.str(R.string.one_install)
    else L10n.str(R.string.x_installs, NumberFormat.getIntegerInstance(L10n.locale()).format(installs))
}
