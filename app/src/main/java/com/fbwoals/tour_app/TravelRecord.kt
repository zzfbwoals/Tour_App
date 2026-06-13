package com.fbwoals.tour_app

// 여행 기록 1건을 화면과 DB 사이에서 주고받는 데이터 모델입니다.
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
    // 대표 사진이 있으면 우선 사용하고, 없으면 저장된 사진 목록/legacy 사진을 순서대로 사용합니다.
    val displayPhotoUri: String?
        get() = coverPhotoUri ?: photoUris.firstOrNull() ?: photoUri
}
