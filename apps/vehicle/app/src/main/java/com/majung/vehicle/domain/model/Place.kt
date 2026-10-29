package com.majung.vehicle.domain.model

/**
 * 검색 결과 시설.
 * registered = 시설 데이터(data/facilities)에 있음. 없으면 일반 길안내만.
 * pickupZoneVerified = 승하차 구역까지 현장 확인됨.
 */
data class Place(
    val name: String,
    val address: String = "",
    val distanceKm: Int = 0,
    val registered: Boolean = true,
    val pickupZoneVerified: Boolean = false,
    val indoorParking: Boolean = false,
)

enum class VisitMode { PICKUP, PARK, DROPOFF }

// ponytail: 시설 검색 API 전까지 Figma 예시 데이터.
val SAMPLE_PLACES = listOf(
    Place("스타필드 하남", "경기 하남시 미사대로 750", 18, pickupZoneVerified = true, indoorParking = true),
    Place("스타필드 코엑스몰", "서울 강남구 영동대로 513", 9, indoorParking = true),
    Place("스타필드 시티 위례", registered = false),
)
