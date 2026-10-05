package com.russellwhite.moodsampler.ui.capture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.russellwhite.moodsampler.appGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launcher for an optional answer photo: returns the action that opens the camera,
 * or null on a device without one. The form owns the attached photo; this only
 * reports a successful capture's content Uri.
 */
@Composable
internal fun rememberPhotoCapture(onCaptured: (Uri) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val photoStore = context.appGraph.photoStore
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    // Survives the camera app killing us in the background.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending?.let(Uri::parse)
        pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        if (ok) onCaptured(uri) else photoStore.discardCapture(uri)
    }
    if (!hasCamera) return null
    return {
        val uri = photoStore.newCaptureUri()
        pending = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pending = null
            photoStore.discardCapture(uri)
        }
    }
}

/** The attached photo with small Retake (when there's a camera) and Remove actions overlaid. */
@Composable
internal fun AttachedPhoto(photo: Uri, onRetake: (() -> Unit)?, onRemove: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        PhotoImage(photo, height = 180.dp, modifier = Modifier.fillMaxWidth())
        Row(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onRetake != null) PhotoAction("Retake", onRetake)
            PhotoAction("Remove", onRemove)
        }
    }
}

@Composable
private fun PhotoAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/** Decodes [uri] off the main thread at roughly the displayed size and shows it, clipped to rounded corners. */
@Composable
internal fun PhotoImage(uri: Uri, height: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { height.roundToPx() * 2 }
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeForDisplay(context, uri, targetPx) }
    }
    Box(modifier.height(height).clip(RoundedCornerShape(12.dp))) {
        bitmap?.let {
            Image(
                it,
                contentDescription = "Photo",
                modifier = Modifier.fillMaxWidth().height(height),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Small square thumbnail for list rows. */
@Composable
internal fun PhotoThumbnail(uri: Uri, size: Dp = 56.dp) {
    PhotoImage(uri, height = size, modifier = Modifier.size(size))
}

private fun decodeForDisplay(context: Context, uri: Uri, targetPx: Int): ImageBitmap? = runCatching {
    val open = { context.contentResolver.openInputStream(uri) }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // With inJustDecodeBounds the decode returns null by design; only the sizes matter.
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
    open()?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }?.asImageBitmap()
}.getOrNull()
