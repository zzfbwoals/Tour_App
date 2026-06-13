package com.fbwoals.tour_app

data class TravelRecord(
    val no: Long = 0,
    val place: String,
    val visitDate: String,
    val memo: String,
    val photoUri: String?,
    val latitude: Double?,
    val longitude: Double?,
    val photoUris: List<String> = photoUri?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
    val coverPhotoUri: String? = photoUri ?: photoUris.firstOrNull()
) {
    val displayPhotoUri: String?
        get() = coverPhotoUri ?: photoUris.firstOrNull() ?: photoUri
}
