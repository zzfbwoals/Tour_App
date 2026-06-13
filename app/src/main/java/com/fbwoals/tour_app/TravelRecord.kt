package com.fbwoals.tour_app

data class TravelRecord(
    val no: Long = 0,
    val place: String,
    val visitDate: String,
    val memo: String,
    val photoUri: String?,
    val latitude: Double?,
    val longitude: Double?
)
