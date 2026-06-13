package com.fbwoals.tour_app

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

// 사진 파일의 EXIF GPS 정보를 읽어 지도 마커 좌표로 변환하는 유틸입니다.
object ExifLocationReader {
    // 사진 URI가 있으면 EXIF에서 위도/경도를 추출하고, 없거나 실패하면 null을 반환합니다.
    fun readLocation(context: Context, uriText: String?): Pair<Double, Double>? {
        if (uriText.isNullOrBlank()) return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriText))?.use { input ->
                val exif = ExifInterface(input)
                val latLong = FloatArray(2)
                // ExifInterface가 GPS 태그를 찾은 경우에만 좌표를 반환합니다.
                if (exif.getLatLong(latLong)) {
                    latLong[0].toDouble() to latLong[1].toDouble()
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
