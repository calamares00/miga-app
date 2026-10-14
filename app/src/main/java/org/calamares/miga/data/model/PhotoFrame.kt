package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * Frame drawn over every recipe photo (lists, search, detail) so photos taken in different places
 * look like one collection. Chosen in Settings > Appearance; drawn by ui/theme/PhotoFrames.kt.
 * Constant names are persisted in the settings, so they must not be renamed.
 */
enum class PhotoFrame(val label: String) {
    NONE(L10n.str(R.string.photo_frame_none)),
    POLAROID(L10n.str(R.string.photo_frame_polaroid)),
    GALLERY(L10n.str(R.string.photo_frame_gallery)),
    FINE_LINE(L10n.str(R.string.photo_frame_fine_line)),
    VIGNETTE(L10n.str(R.string.photo_frame_vignette)),
    ACCENT(L10n.str(R.string.photo_frame_accent));

    companion object {
        val DEFAULT = NONE
    }
}
