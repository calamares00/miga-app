package org.calamares.miga.data.remote

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build

/** Where Miga was installed from, to send each user to the place that updates their copy. */
object AppInstallSource {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** F-Droid and the clients that install from its repository. */
    private val FDROID_CLIENTS = setOf(
        "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid", "eu.bubu1.fdroidclassic"
    )

    fun isGooglePlay(context: Context): Boolean = installerOf(context) == PLAY_STORE_PACKAGE

    fun isFdroid(context: Context): Boolean = installerOf(context) in FDROID_CLIENTS

    @Suppress("DEPRECATION")
    private fun installerOf(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    /** Opens Miga's page in the Play Store app, or on the web when the app is missing. */
    fun openStorePage(context: Context) {
        val packageName = context.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            .setPackage(PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
