package org.calamares.miga.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.PhotoStorage

/** Opens the system camera app; see [rememberCameraCapture]. */
class CameraCapture internal constructor(private val start: () -> Unit) {
    fun launch() = start()
}

/**
 * Takes a photo with the system camera app into a new file of [PhotoStorage] and hands its path to
 * [onPhoto].
 *
 * The barcode scanner library adds the CAMERA permission to the app, and Android refuses to start
 * the camera app (SecurityException) for an app that declares CAMERA without holding it: denied,
 * or revoked by the system because it was not used for a while. So the permission is asked first
 * when missing; if the user refuses, a message says the gallery is still there. The target path
 * survives the activity being recreated while the camera app is in front.
 */
@Composable
fun rememberCameraCapture(onPhoto: (filePath: String) -> Unit): CameraCapture {
    val context = LocalContext.current
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPath
        pendingPath = null
        if (success && path != null) currentOnPhoto(path)
    }
    val openCamera = {
        val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
        pendingPath = filePath
        try {
            takePicture.launch(contentUri)
        } catch (e: SecurityException) {
            pendingPath = null
            Toast.makeText(context, L10n.str(R.string.camera_permission_needed), Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            pendingPath = null
            Toast.makeText(context, L10n.str(R.string.no_camera_app), Toast.LENGTH_LONG).show()
        }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
        else Toast.makeText(context, L10n.str(R.string.camera_permission_needed), Toast.LENGTH_LONG).show()
    }
    return remember(takePicture, askPermission) {
        CameraCapture {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
            else askPermission.launch(Manifest.permission.CAMERA)
        }
    }
}
