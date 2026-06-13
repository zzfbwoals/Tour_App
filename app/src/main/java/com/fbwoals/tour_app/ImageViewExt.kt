package com.fbwoals.tour_app

import android.net.Uri
import android.widget.ImageView

// 여행 사진 URI를 ImageView에 안전하게 표시하는 확장 함수입니다.
fun ImageView.loadTravelImage(uriText: String?) {
    if (uriText.isNullOrBlank()) {
        setImageResource(R.drawable.ic_image)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(R.color.surface_variant)
    } else {
        scaleType = ImageView.ScaleType.CENTER_CROP
        // 포토피커 권한이 만료된 URI 등은 앱 종료 대신 기본 이미지로 대체합니다.
        runCatching {
            setImageURI(Uri.parse(uriText))
        }.onFailure {
            setImageResource(R.drawable.ic_image)
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundResource(R.color.surface_variant)
        }
    }
}
