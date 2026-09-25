package com.siren.mobile.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

interface ProfilePhotoPicker {

    suspend fun pickProfilePhoto(): String?
}

object ProfilePhotoEncoder {

    private const val TAG = "ProfilePhoto"

    fun encode(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Could not read image bounds from $uri")
            return null
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val scaled = scaleToBound(decoded)
        if (scaled !== decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, PROFILE_PHOTO_QUALITY, out)
        scaled.recycle()

        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.onFailure { Log.e(TAG, "Failed to encode profile photo", it) }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= PROFILE_PHOTO_MAX_PX) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToBound(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= PROFILE_PHOTO_MAX_PX) return source
        val ratio = PROFILE_PHOTO_MAX_PX.toFloat() / longest
        val w = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
