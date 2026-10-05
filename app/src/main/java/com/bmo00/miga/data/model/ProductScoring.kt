package com.bmo00.miga.data.model

import kotlin.math.roundToInt

/** Tramo de la puntuación (mismos cuatro niveles que usan apps de este tipo: excelente, bueno, mediocre, malo). */
enum class ScoreTier(val label: String, val argb: Long) {
    EXCELLENT("Excelente", 0xFF1E9E4A),
    GOOD("Bueno", 0xFF8BC34A),
    MEDIOCRE("Mediocre", 0xFFF5A623),
    BAD("Malo", 0xFFE53935)
}

enum class AdditiveRisk(val label: String) {
    NONE("sin riesgo conocido"),
    LIMITED("riesgo limitado"),
    MODERATE("riesgo moderado"),
    HIGH("riesgo alto")
}

/**
 * Desglose de la puntuación: [nutrition] y [additives] son subnotas de 0 a 100 (pesan 60 % y 30 %),
 * [organicBonus] suma hasta 10 puntos y [cappedByRiskyAdditive] indica que un aditivo de riesgo alto
 * ha limitado la nota a 49.
 */
data class ProductScore(
    val value: Int,
    val tier: ScoreTier,
    val nutrition: Int,
    val additives: Int,
    val organicBonus: Int,
    val cappedByRiskyAdditive: Boolean
)

/**
 * Estimación PROPIA de una puntuación de 0 a 100 a partir de los datos de Open Food Facts: 60 % calidad
 * nutricional según el Nutri-Score, 30 % aditivos y 10 % ecológico. La tabla de riesgo de aditivos es
 * una aproximación basada en clasificaciones públicas (EFSA/IARC, controversias habituales).
 * Lógica pura, sin Android.
 */
object ProductScoring {

    /** Puntos del Nutri-Score (-15 mejor ... 40 peor) -> subnota 0..100, alineada con los cortes de las letras A-E. */
    private val nutritionAnchors = listOf(
        -15 to 100, -1 to 80, 0 to 79, 2 to 65, 3 to 60, 10 to 40, 11 to 38, 18 to 15, 19 to 12, 40 to 0
    )

    private val gradeFallback = mapOf("a" to 90, "b" to 72, "c" to 50, "d" to 28, "e" to 8)

    private val highRisk = setOf(
        "e171", "e249", "e250", "e251", "e252", "e123", "e128", "e924", "e320", "e216", "e217", "e231", "e232", "e239"
    )

    private val moderateRisk = setOf(
        "e102", "e104", "e110", "e122", "e124", "e129", "e131", "e133", "e142", "e150c", "e150d", "e151", "e155",
        "e173", "e210", "e211", "e212", "e213", "e220", "e221", "e222", "e223", "e224", "e226", "e227", "e228",
        "e310", "e311", "e312", "e321", "e338", "e339", "e340", "e341", "e343", "e450", "e451", "e452",
        "e407", "e407a", "e432", "e433", "e434", "e435", "e436", "e466",
        "e950", "e951", "e952", "e954", "e955", "e621", "e622", "e623", "e624", "e625"
    )

    private val limitedRisk = setOf(
        "e150b", "e471", "e472a", "e472b", "e472c", "e472e", "e473", "e475", "e476", "e481", "e482",
        "e491", "e492", "e953", "e420", "e421", "e965", "e966", "e967", "e968"
    )

    fun additiveRisk(code: String): AdditiveRisk {
        val key = code.trim().lowercase()
        return when {
            key in highRisk -> AdditiveRisk.HIGH
            key in moderateRisk -> AdditiveRisk.MODERATE
            key in limitedRisk -> AdditiveRisk.LIMITED
            else -> AdditiveRisk.NONE
        }
    }

    fun tierFor(value: Int): ScoreTier = when {
        value >= 75 -> ScoreTier.EXCELLENT
        value >= 50 -> ScoreTier.GOOD
        value >= 25 -> ScoreTier.MEDIOCRE
        else -> ScoreTier.BAD
    }

    /** Subnota nutricional (0..100) o null si no hay ni puntos ni letra del Nutri-Score. */
    fun nutritionSubscore(info: ProductInfo): Int? {
        info.nutriScoreValue?.let { return interpolate(it) }
        return gradeFallback[info.nutriScore?.lowercase()]
    }

    /** Subnota de aditivos (0..100) y si hay alguno de riesgo alto. Sin lista de aditivos se asume que no hay ninguno. */
    fun additivesSubscore(additives: List<String>?): Pair<Int, Boolean> {
        val risks = additives.orEmpty().map { additiveRisk(it) }
        if (AdditiveRisk.HIGH in risks) return 0 to true
        // fold en vez de sumOf: con literales enteros en las ramas, sumOf es ambiguo entre sus sobrecargas (Int/Long/...).
        val penalty = risks.fold(0) { total, risk ->
            total + when (risk) {
                AdditiveRisk.MODERATE -> 25
                AdditiveRisk.LIMITED -> 10
                else -> 0
            }
        }
        return (100 - penalty).coerceAtLeast(0) to false
    }

    /** null si no se puede calcular (sin Nutri-Score: p. ej. agua, o producto sin datos nutricionales). */
    fun compute(info: ProductInfo): ProductScore? {
        val nutrition = nutritionSubscore(info) ?: return null
        val (additives, risky) = additivesSubscore(info.additives)
        val organic = if ("organic" in info.labels) 10 else 0
        var total = (0.6 * nutrition + 0.3 * additives + organic).roundToInt().coerceIn(0, 100)
        if (risky) total = minOf(total, 49)
        return ProductScore(total, tierFor(total), nutrition, additives, organic, risky)
    }

    private fun interpolate(points: Int): Int {
        val first = nutritionAnchors.first()
        val last = nutritionAnchors.last()
        if (points <= first.first) return first.second
        if (points >= last.first) return last.second
        for (i in 0 until nutritionAnchors.size - 1) {
            val (x0, y0) = nutritionAnchors[i]
            val (x1, y1) = nutritionAnchors[i + 1]
            if (points in x0..x1) {
                val t = (points - x0).toDouble() / (x1 - x0)
                return (y0 + t * (y1 - y0)).roundToInt()
            }
        }
        return last.second
    }
}
