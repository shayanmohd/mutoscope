package com.mohdshayan.mutoscope.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mohdshayan.mutoscope.core.io.DecodeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The must-not-crash path for reference photos. Decodes on IO at the size the canvas needs,
 * never the full photo: ImageDecoder with a target size on Android 9 and up; a bounds pass, a
 * power-of-two sample size and the EXIF rotation on 8. Anything over 50 megapixels is refused, and
 * any failure, out-of-memory included, returns null so the project is left untouched.
 */
object ReferenceDecoder {

    private class TooLarge : Exception()

    suspend fun decode(context: Context, uri: Uri, canvasW: Int, canvasH: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val photo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) modern(context, uri, canvasW, canvasH) else legacy(context, uri, canvasW, canvasH)
            onCanvas(photo, canvasW, canvasH)
        } catch (e: Throwable) {
            Log.w("Mutoscope", "Reference photo not decoded", e)
            null
        }
    }

    private fun modern(context: Context, uri: Uri, canvasW: Int, canvasH: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            if (DecodeMath.isTooLarge(w, h)) throw TooLarge()
            val fit = DecodeMath.fitInside(w, h, canvasW, canvasH)
            decoder.setTargetSize(fit.first, fit.second)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun legacy(context: Context, uri: Uri, canvasW: Int, canvasH: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw IllegalStateException()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException()
        if (DecodeMath.isTooLarge(bounds.outWidth, bounds.outHeight)) throw TooLarge()
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
        // Sample against the box as the unrotated image sees it.
        val boxW = if (rotation % 180 != 0) canvasH else canvasW
        val boxH = if (rotation % 180 != 0) canvasW else canvasH
        val opts = BitmapFactory.Options().apply {
            inSampleSize = DecodeMath.sampleSize(bounds.outWidth, bounds.outHeight, boxW, boxH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IllegalStateException()
        if (rotation == 0) return raw
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    /** Centres the photo on a transparent canvas-sized cel, scaled to fit. */
    private fun onCanvas(photo: Bitmap, canvasW: Int, canvasH: Int): Bitmap {
        val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val scale = minOf(canvasW.toFloat() / photo.width, canvasH.toFloat() / photo.height)
        val w = photo.width * scale
        val h = photo.height * scale
        val left = (canvasW - w) / 2f
        val top = (canvasH - h) / 2f
        Canvas(out).drawBitmap(photo, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
