package org.calamares.miga.data.remote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Where Miga was installed from, to send each user to the place that updates their copy. */
object AppInstallSource {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isGooglePlay(context: Context): Boolean = installerOf(context) == PLAY_STORE_PACKAGE

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
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .setPackage(PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
