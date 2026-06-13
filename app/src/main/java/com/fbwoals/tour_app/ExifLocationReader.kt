package com.fbwoals.tour_app

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ExifLocationReader {
    fun readLocation(context: Context, uriText: String?): Pair<Double, Double>? {
        if (uriText.isNullOrBlank()) return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriText))?.use { input ->
                val exif = ExifInterface(input)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    latLong[0].toDouble() to latLong[1].toDouble()
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
