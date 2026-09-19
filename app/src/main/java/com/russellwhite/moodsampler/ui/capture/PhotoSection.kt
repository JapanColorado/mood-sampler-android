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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * Optional photo for the answer. [photo] is what's currently attached (a stored
 * photo's file Uri, or a fresh capture's content Uri); the section only reports
 * changes, the form owns the state.
 */
@Composable
internal fun PhotoSection(
    photo: Uri?,
    onCaptured: (Uri) -> Unit,
    onRemoved: () -> Unit,
) {
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
    fun takePhoto() {
        val uri = photoStore.newCaptureUri()
        pending = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pending = null
            photoStore.discardCapture(uri)
        }
    }

    if (!hasCamera && photo == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add a photo? (optional)", style = MaterialTheme.typography.titleMedium)
        if (photo == null) {
            OutlinedButton(onClick = ::takePhoto) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("  Take photo")
            }
        } else {
            PhotoImage(photo, height = 220.dp, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasCamera) TextButton(onClick = ::takePhoto) { Text("Retake") }
                TextButton(onClick = onRemoved) { Text("Remove") }
            }
        }
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
