package org.calamares.miga.data.update

import org.calamares.miga.R
import org.calamares.miga.data.remote.AppUpdateClient
import kotlin.random.Random

/**
 * The thank-you to closed beta testers once Miga is public, offering to move to the public
 * version (leaving the testing programme keeps the app and its data, as both are signed alike).
 *
 * Who is a tester: whoever first ran a version older than [PUBLIC_VERSION], as there was no public
 * version before it. When: once [PUBLIC_VERSION] is the latest stable GitHub Release, which CI
 * marks when it reaches 100 % in production. A tester who stays in the beta is reminded every
 * [REMIND_AFTER_DAYS] days; closing the message without choosing brings it back the next day.
 */
object TesterThanks {

    /** The first version released to the public. */
    const val PUBLIC_VERSION = "1.1.0"

    const val REMIND_AFTER_DAYS = 14L
    const val REMIND_AFTER_DISMISS_DAYS = 1L

    /** Stored instead of a day when the tester chose the public version: never shown again. */
    const val NEVER = -1L

    /** Where a tester leaves the closed beta (Play's page for the testing programme). */
    const val LEAVE_BETA_URL = "https://play.google.com/apps/testing/org.calamares.miga"

    /** Each message as its title and text; one is picked at random every time. */
    val MESSAGES: List<Pair<Int, Int>> = listOf(
        R.string.tester_thanks_title_oven to R.string.tester_thanks_body_oven,
        R.string.tester_thanks_title_taster to R.string.tester_thanks_body_taster,
        R.string.tester_thanks_title_sofrito to R.string.tester_thanks_body_sofrito,
        R.string.tester_thanks_title_croqueta to R.string.tester_thanks_body_croqueta,
        R.string.tester_thanks_title_short to R.string.tester_thanks_body_short
    )

    /** Whether someone whose first run was [firstVersionName] took part in the beta. */
    fun isTester(firstVersionName: String): Boolean = AppUpdateClient.isNewer(PUBLIC_VERSION, firstVersionName)

    /**
     * Whether to thank now: a tester, Miga already public ([latestStable] is [PUBLIC_VERSION] or
     * later), and the message is due ([nextDay] is null the first time).
     */
    fun isDue(isTester: Boolean, latestStable: String?, nextDay: Long?, today: Long): Boolean =
        isTester && latestStable != null && !AppUpdateClient.isNewer(PUBLIC_VERSION, latestStable) &&
            nextDay != NEVER && (nextDay == null || today >= nextDay)

    fun pick(random: Random = Random.Default): Int = random.nextInt(MESSAGES.size)
}
