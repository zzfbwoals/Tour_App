package com.fbwoals.tour_app

import android.net.Uri
import android.widget.ImageView

fun ImageView.loadTravelImage(uriText: String?) {
    if (uriText.isNullOrBlank()) {
        setImageResource(R.drawable.ic_image)
        scaleType = ImageView.ScaleType.CENTER
        setBackgroundResource(R.color.surface_variant)
    } else {
        scaleType = ImageView.ScaleType.CENTER_CROP
        runCatching {
            setImageURI(Uri.parse(uriText))
        }.onFailure {
            setImageResource(R.drawable.ic_image)
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundResource(R.color.surface_variant)
        }
    }
}
