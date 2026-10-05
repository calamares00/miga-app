package org.calamares.miga.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Artículo de una plantilla. Si viene de Open Food Facts conserva su foto y su ficha ([info]) para que,
 * al aplicar la plantilla, el producto llegue a la lista igual que si se hubiera escaneado.
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

/** Plantilla de lista de la compra: un nombre y sus artículos; [id] > 0 si es del usuario (guardada en Room). */
data class ShoppingTemplate(
    val id: Long,
    val name: String,
    val items: List<TemplateItem>
) {
    val isPredefined: Boolean get() = id < 0
}

/**
 * Formato del cuerpo de una plantilla en la base de datos: JSON con la lista de [TemplateItem].
 * Las plantillas antiguas (una línea "cantidad<TAB>unidad<TAB>nombre" por artículo) se siguen leyendo
 * con [legacyDecoder]. Lógica pura, sin Android.
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
                // No es JSON válido: se intenta como formato antiguo.
            }
        }
        return legacyDecoder(body).map { TemplateItem.of(it) }
    }

    /**
     * Añade [item] a [items]: si ya hay uno con el mismo nombre (sin distinguir mayúsculas) lo sustituye
     * conservando su posición, para no duplicar el mismo producto en la plantilla.
     */
    fun upsert(items: List<TemplateItem>, item: TemplateItem): List<TemplateItem> {
        val key = item.name.trim().lowercase()
        val index = items.indexOfFirst { it.name.trim().lowercase() == key }
        return if (index >= 0) items.toMutableList().also { it[index] = item } else items + item
    }
}
