package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * Colours for a book's cover when it has no photo. The first seven, in this order, are the ones
 * picked from the book's name when the user chooses none (see [forName]): books keep the colour
 * they always had. Names are stored and synced; do not rename them.
 */
enum class BookColor(val argb: Long, private val labelRes: Int) {
    TERRACOTTA(0xFFC1633D, R.string.terracotta),
    BLUE(0xFF3E8EB5, R.string.blue),
    GREEN(0xFF5C9E4B, R.string.green),
    PURPLE(0xFF8E6BB5, R.string.purple),
    PINK(0xFFD1709F, R.string.pink),
    MUSTARD(0xFFD9962B, R.string.orange),
    TEAL(0xFF3FA39B, R.string.turquoise),
    GARDEN(0xFF2F7A57, R.string.book_color_garden),
    WINE(0xFF8C2F45, R.string.book_color_wine),
    GRAPHITE(0xFF4A5560, R.string.book_color_graphite);

    val label: String get() = L10n.str(labelRes)

    companion object {
        private val FROM_NAME = entries.take(7)

        fun fromName(name: String?): BookColor? = entries.firstOrNull { it.name == name }

        /** The colour a book shows: the chosen one, or one picked from its name. */
        fun of(chosen: String?, bookName: String): BookColor =
            fromName(chosen) ?: FROM_NAME[Math.floorMod(bookName.hashCode(), FROM_NAME.size)]
    }
}

/**
 * Emojis offered for a cover without a photo: dishes, ingredients and occasions. All of them are
 * in Emoji 5.0 or older, so Android 8 (the oldest Miga runs on) draws them.
 */
val BOOK_EMOJIS = listOf(
    "🍰", "🎂", "🍪", "🥐", "🍞", "🥘", "🍲", "🍝", "🍜", "🥗", "🍳", "🐟",
    "🥩", "🍗", "🍅", "🥕", "🌶️", "🍋", "🍓", "🧀", "🥑", "🍷", "☕", "🎄"
)
