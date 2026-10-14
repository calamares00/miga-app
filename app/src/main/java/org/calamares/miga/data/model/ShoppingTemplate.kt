package org.calamares.miga.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * An item in a shopping template. Items from Open Food Facts keep their photo and product sheet
 * ([info]) so that applying the template adds them exactly as if they had been scanned.
 */
@Serializable
data class TemplateItem(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val imageUrl: String? = null,
    val info: ProductInfo? = null
) {
    val isProduct: Boolean get() = info != null || imageUrl != null

    fun toEntry(): ParsedShoppingEntry = ParsedShoppingEntry(name, quantity, unit)

    companion object {
        fun of(entry: ParsedShoppingEntry) = TemplateItem(entry.name, entry.quantity, entry.unit)
    }
}

/**
 * A shopping list template: a name and its items. User templates are stored in Room with [id] > 0;
 * predefined ones use negative ids.
 */
data class ShoppingTemplate(
    val id: Long,
    val name: String,
    val items: List<TemplateItem>
) {
    val isPredefined: Boolean get() = id < 0
}

/**
 * Storage format of a template body: a JSON list of [TemplateItem]. Older templates (one
 * "quantity<TAB>unit<TAB>name" line per item) are still read through [legacyDecoder]. Pure logic.
 */
object ShoppingTemplateCodec {
    private const val MAX_ITEMS = 500
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(TemplateItem.serializer())

    fun encode(items: List<TemplateItem>): String =
        json.encodeToString(serializer, items.filter { it.name.isNotBlank() }.take(MAX_ITEMS))

    fun decode(body: String, legacyDecoder: (String) -> List<ParsedShoppingEntry>): List<TemplateItem> {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) {
            try {
                return json.decodeFromString(serializer, trimmed).filter { it.name.isNotBlank() }
            } catch (e: IllegalArgumentException) {
                // Not valid JSON: fall back to the legacy format.
            }
        }
        return legacyDecoder(body).map { TemplateItem.of(it) }
    }

    /**
     * Adds [item] to [items]. An item with the same name (case-insensitive) is replaced in place so
     * the template never lists the same product twice.
     */
    fun upsert(items: List<TemplateItem>, item: TemplateItem): List<TemplateItem> {
        val key = item.name.trim().lowercase()
        val index = items.indexOfFirst { it.name.trim().lowercase() == key }
        return if (index >= 0) items.toMutableList().also { it[index] = item } else items + item
    }
}
