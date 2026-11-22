package com.example.vision

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Cálculo de tamaños, separado de Bitmap para poder probarlo en JVM. */
object ImageScale {
    const val MAX_SIDE = 1024

    /** Potencia de 2 que reduce la decodificación sin bajar de [max] en el lado mayor. */
    fun sampleSize(width: Int, height: Int, max: Int = MAX_SIDE): Int {
        var sample = 1
        while (width / (sample * 2) >= max && height / (sample * 2) >= max) sample *= 2
        return sample
    }

    /** Tamaño final con el lado mayor ≤ [max], conservando la proporción y sin ampliar. */
    fun target(width: Int, height: Int, max: Int = MAX_SIDE): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= max) return width to height
        val ratio = max.toFloat() / longest
        return maxOf(1, Math.round(width * ratio)) to maxOf(1, Math.round(height * ratio))
    }
}

/** Una imagen lista para adjuntar: JPEG ligero (≤1024 px) ya guardado en el teléfono. */
data class PendingImage(val path: String, val width: Int, val height: Int, val bytes: Int)

/** Reduce a 1024 px como máximo, corrige la rotación EXIF y comprime a JPEG. Ejecutar fuera del hilo principal. */
class ImageCompressor(private val resolver: ContentResolver, private val outDir: File) {

    fun compress(uri: Uri): PendingImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val opts = BitmapFactory.Options().apply { inSampleSize = ImageScale.sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null

        val rotation = resolver.openInputStream(uri)?.use { exifDegrees(it) } ?: 0
        val (w, h) = ImageScale.target(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (w != decoded.width) postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val moved = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // JPEG no tiene transparencia: se pinta sobre blanco para que un PNG transparente no quede negro
        val flat = Bitmap.createBitmap(moved.width, moved.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(moved, 0f, 0f, null) }

        var quality = 82
        var data: ByteArray
        do {
            data = ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            quality -= 10
        } while (data.size > MAX_BYTES && quality >= 42)

        outDir.mkdirs()
        val file = File(outDir, "${UUID.randomUUID()}.jpg").apply { writeBytes(data) }
        PendingImage(file.absolutePath, flat.width, flat.height, data.size)
    }.getOrNull()

    private fun exifDegrees(input: java.io.InputStream): Int = runCatching {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private companion object {
        const val MAX_BYTES = 450_000
    }
}
