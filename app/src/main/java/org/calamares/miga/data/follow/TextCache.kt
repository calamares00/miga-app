package org.calamares.miga.data.follow

/**
 * Remembers a value worked out from a text, for the latest [capacity] texts (least recently used
 * go first). Thread-safe: feeds are worked out on background threads.
 */
internal class TextCache<T>(private val capacity: Int = 4000) {

    private val values = object : LinkedHashMap<String, T>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = size > capacity
    }

    fun get(key: String, compute: () -> T): T {
        synchronized(values) { values[key]?.let { return it } }
        val value = compute()
        synchronized(values) { values[key] = value }
        return value
    }
}
