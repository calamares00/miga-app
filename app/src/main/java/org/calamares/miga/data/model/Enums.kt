package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/** Recipe difficulty. [name] is what gets stored in the database, backups, packs and sync. */
enum class Difficulty(val label: String) {
    EASY(L10n.str(R.string.easy)),
    MEDIUM(L10n.str(R.string.medium)),
    HARD(L10n.str(R.string.hard));

    companion object {
        /**
         * Parses a stored or AI-provided value. Accepts the legacy Spanish names (FACIL, MEDIA,
         * DIFICIL) still found in old backups, recipe packs and data from older app versions.
         */
        fun parse(value: String?): Difficulty = when (value?.trim()?.uppercase()) {
            "EASY", "FACIL" -> EASY
            "HARD", "DIFICIL" -> HARD
            else -> MEDIUM
        }
    }
}

enum class SortOption(val label: String) {
    NAME_ASC(L10n.str(R.string.name_z)),
    RECENT(L10n.str(R.string.newest)),
    MOST_COOKED(L10n.str(R.string.most_cooked)),
    PREP_TIME(L10n.str(R.string.prep_time)),
    BEST_RATED(L10n.str(R.string.top_rated))
}
