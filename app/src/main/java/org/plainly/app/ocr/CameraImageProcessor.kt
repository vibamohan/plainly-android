package org.plainly.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Corrects camera orientation and trims a small edge margin before OCR. */
object CameraImageProcessor {
    fun normalize(context: Context, source: File, additionalRotationDegrees: Int = 0, cropPercent: Int = 3): File {
        val cropped = preview(source, additionalRotationDegrees, cropPercent)
        val output = File.createTempFile("normalized-", ".jpg", context.cacheDir)
        try {
            FileOutputStream(output).use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            return output
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally { cropped.recycle() }
    }

    /** Shared by preview and OCR. Caller runs off the UI thread and owns the returned bitmap. */
    fun preview(source: File, additionalRotationDegrees: Int = 0, cropPercent: Int = 3): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not decode camera image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2500) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode camera image")
        val orientation = ExifInterface(source.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val corrected = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(decoded, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(decoded, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(decoded, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flip(decoded, true)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flip(decoded, false)
            ExifInterface.ORIENTATION_TRANSPOSE -> transform(decoded, 90f, true)
            ExifInterface.ORIENTATION_TRANSVERSE -> transform(decoded, 270f, true)
            else -> decoded
        }
        if (corrected !== decoded) decoded.recycle()
        val rotated = if (additionalRotationDegrees % 360 != 0) {
            rotate(corrected, (additionalRotationDegrees % 360).toFloat()).also {
                if (it !== corrected) corrected.recycle()
            }
        } else corrected
        val safeCrop = cropPercent.coerceIn(0, 20)
        val marginX = (rotated.width * (safeCrop / 100f)).toInt().coerceAtMost(rotated.width / 4)
        val marginY = (rotated.height * (safeCrop / 100f)).toInt().coerceAtMost(rotated.height / 4)
        val cropped = Bitmap.createBitmap(
            rotated, marginX, marginY,
            (rotated.width - marginX * 2).coerceAtLeast(1),
            (rotated.height - marginY * 2).coerceAtLeast(1)
        )
        if (cropped !== rotated) rotated.recycle()
        return cropped
    }

    private fun transform(bitmap: Bitmap, degrees: Float, mirror: Boolean): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply {
            postRotate(degrees)
            if (mirror) postScale(-1f, 1f)
        }, true)

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)

    private fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f) }, true)
}
