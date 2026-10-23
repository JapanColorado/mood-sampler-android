package dev.russell.moodsampler.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photos attached to answers. The camera app writes into a cache "capture" file
 * (shared via FileProvider); on save the picture is downscaled, rotated upright
 * and moved into private app storage, and the sample keeps only the file name.
 */
class PhotoStore(private val context: Context) {

    private val captureDir get() = File(context.cacheDir, CAPTURE_DIR)
    private val photoDir get() = File(context.filesDir, PHOTO_DIR)

    /** A fresh content Uri the camera app can write to; stale captures are pruned. */
    fun newCaptureUri(): Uri {
        captureDir.mkdirs()
        val cutoff = System.currentTimeMillis() - STALE_CAPTURE_MS
        captureDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val file = File(captureDir, "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Move a finished capture into permanent storage; returns the stored file name. */
    suspend fun commit(captureUri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(captureUri)?.use { decodeScaled(it.readBytes()) }
            ?: error("Could not read the photo.")
        photoDir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        File(photoDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        discardCapture(captureUri)
        name
    }

    /**
     * [uri] (a capture or a stored photo) re-encoded as a smaller upright JPEG for
     * sending to Claude, or null if it can't be read.
     */
    suspend fun jpegForUpload(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { decodeScaled(it.readBytes(), UPLOAD_EDGE_PX) }
                ?: return@runCatching null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }.getOrNull()
    }

    /** Drop an unsaved capture (the user removed it, or the answer was never saved). */
    fun discardCapture(captureUri: Uri) {
        captureUri.lastPathSegment?.let { File(captureDir, it).delete() }
    }

    fun file(name: String): File = File(photoDir, name)

    fun uri(name: String): Uri = Uri.fromFile(file(name))

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        photoDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** After a restore: keep only the photos the restored samples still point at. */
    suspend fun retainOnly(names: Collection<String>) = withContext(Dispatchers.IO) {
        val keep = names.toSet()
        photoDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        Unit
    }

    /** Decode to at most about [maxEdgePx] on the long side and apply the EXIF orientation. */
    private fun decodeScaled(bytes: ByteArray, maxEdgePx: Int = MAX_EDGE_PX): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdgePx) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode the photo.")
        val orientation = bytes.inputStream().use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return decoded
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private companion object {
        const val CAPTURE_DIR = "photo_captures"
        const val PHOTO_DIR = "photos"
        const val MAX_EDGE_PX = 1600
        const val UPLOAD_EDGE_PX = 1024
        const val JPEG_QUALITY = 85
        const val STALE_CAPTURE_MS = 24 * 60 * 60 * 1000L
    }
}
