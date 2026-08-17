package com.moonforce.ohmyainote.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.moonforce.ohmyainote.document.model.SourceFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportProcessors(private val context: Context) {
    suspend fun copyUri(uri: Uri, suffix: String): Path = withContext(Dispatchers.IO) {
        val target = context.cacheDir.toPath().resolve("import-${UUID.randomUUID()}$suffix")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            Files.copy(input, target)
        }
        target
    }

    suspend fun bakeImage(uri: Uri): SourceFile = withContext(Dispatchers.IO) {
        val orientation = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_IMAGE_EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("Unable to decode image")
        val baked = applyOrientation(decoded, orientation)
        if (baked !== decoded) decoded.recycle()
        val flattened = Bitmap.createBitmap(baked.width, baked.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(baked, 0f, 0f, null)
        }
        baked.recycle()
        val target = context.cacheDir.toPath().resolve("image-${UUID.randomUUID()}.jpg")
        Files.newOutputStream(target).use { output ->
            check(flattened.compress(Bitmap.CompressFormat.JPEG, 92, output))
        }
        val longest = max(flattened.width, flattened.height).toFloat()
        val widthPt = flattened.width / longest * 792f
        val heightPt = flattened.height / longest * 792f
        flattened.recycle()
        SourceFile(target, widthPt, heightPt)
    }

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private companion object {
        const val MAX_IMAGE_EDGE = 4096
    }
}
