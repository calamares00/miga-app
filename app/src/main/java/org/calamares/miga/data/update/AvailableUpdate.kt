package org.calamares.miga.data.update

/**
 * A newer Miga the user can install, found by the build's UpdateSource (Google Play's in-app
 * updates in the play build, the latest stable GitHub Release in the fdroid one).
 *
 * @param key identifies the version, to remember a "Not now".
 * @param versionName the new version, when known (Play only tells its version code).
 * @param pageUrl where to get it, for the builds that send the user to a web page.
 */
data class AvailableUpdate(val key: String, val versionName: String?, val pageUrl: String? = null)
