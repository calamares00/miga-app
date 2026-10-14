package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlin.math.roundToInt

/** Score band, with the four levels these apps usually use: excellent, good, mediocre, poor. */
enum class ScoreTier(val label: String, val argb: Long) {
    EXCELLENT(L10n.str(R.string.excellent), 0xFF1E9E4A),
    GOOD(L10n.str(R.string.good), 0xFF8BC34A),
    MEDIOCRE(L10n.str(R.string.mediocre), 0xFFF5A623),
    BAD(L10n.str(R.string.poor), 0xFFE53935)
}

enum class AdditiveRisk(val label: String) {
    NONE(L10n.str(R.string.no_known_risk)),
    LIMITED(L10n.str(R.string.limited_risk)),
    MODERATE(L10n.str(R.string.moderate_risk)),
    HIGH(L10n.str(R.string.high_risk))
}

/**
 * Score breakdown: [nutrition] and [additives] are 0-100 subscores (60 % and 30 % of the total),
 * [organicBonus] adds up to 10 points and [cappedByRiskyAdditive] tells that a high-risk additive
 * capped the score at 49.
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
 * Miga's OWN 0-100 product score estimate from Open Food Facts data: 60 % nutritional quality from
 * the Nutri-Score, 30 % additives and 10 % organic. The additive risk table approximates public
 * classifications (EFSA/IARC and common controversies). Pure logic, no Android dependencies.
 */
object ProductScoring {

    /**
     * Nutri-Score points (-15 best ... 40 worst) to a 0-100 subscore, aligned with the A-E grade
     * boundaries.
     */
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

    /**
     * Nutrition subscore (0-100), or null when there are neither Nutri-Score points nor a grade.
     */
    fun nutritionSubscore(info: ProductInfo): Int? {
        info.nutriScoreValue?.let { return interpolate(it) }
        return gradeFallback[info.nutriScore?.lowercase()]
    }

    /**
     * Additives subscore (0-100) and whether any of them is high risk. Without an additive list
     * none are assumed.
     */
    fun additivesSubscore(additives: List<String>?): Pair<Int, Boolean> {
        val risks = additives.orEmpty().map { additiveRisk(it) }
        if (AdditiveRisk.HIGH in risks) return 0 to true
        // fold instead of sumOf: with integer literals in the branches sumOf is ambiguous between
        // its overloads.
        val penalty = risks.fold(0) { total, risk ->
            total + when (risk) {
                AdditiveRisk.MODERATE -> 25
                AdditiveRisk.LIMITED -> 10
                else -> 0
            }
        }
        return (100 - penalty).coerceAtLeast(0) to false
    }

    /**
     * Null when the score cannot be computed (no Nutri-Score, for example water or products without
     * nutrition data).
     */
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
