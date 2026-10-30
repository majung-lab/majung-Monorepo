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
    val id: String = name,
)

enum class VisitMode { PICKUP, PARK, DROPOFF }

// ponytail: 시설 검색 API 전까지 Figma 예시 데이터.
val SAMPLE_PLACES = listOf(
    Place("스타필드 하남", "경기 하남시 미사대로 750", 18, pickupZoneVerified = true, indoorParking = true),
    Place("스타필드 코엑스몰", "서울 강남구 영동대로 513", 9, indoorParking = true),
    Place("스타필드 시티 위례", registered = false),
)

/** 시설 안 승하차 지점 */
data class AccessPoint(
    val name: String,
    val reason: String,
    val tags: List<String>,
    val recommended: Boolean = false,
    /** 지도·가족 화면에 보이는 만날 곳 이름 (Figma: C04 "북문 승하차 구역" → C06 "북문 로비") */
    val meetName: String = name,
    val id: String = name,
)

// ponytail: 날씨·시설 API 전까지 Figma 예시.
const val SAMPLE_WEATHER = "15시부터 비 · 강수확률 70%"
val SAMPLE_ACCESS_POINTS = listOf(
    AccessPoint("북문 승하차 구역", "비 예보라 지붕 아래에서 내려 바로 실내로 들어가요", listOf("지붕 있음", "정차 3분"), recommended = true, meetName = "북문 로비"),
    AccessPoint("정문 앞 도로", "입구는 가깝지만 지붕이 없고 차가 많아요", listOf("지붕 없음")),
)

/** 목적지 기록 한 줄 */
data class VisitRecord(
    val place: String,
    val date: String,
    val mode: VisitMode,
    val outcome: String,
    val weatherTime: String,
)

val VisitMode.label: String
    get() = when (this) {
        VisitMode.PICKUP -> "픽업하기"
        VisitMode.PARK -> "그냥가기"
        VisitMode.DROPOFF -> "내려주기"
    }

// ponytail: 기록 API 전까지 Figma 예시.
val SAMPLE_RECORDS = listOf(
    VisitRecord("스타필드 하남", "10/26 (일)", VisitMode.PICKUP, "북문 로비에서 태움", "비 · 14:52"),
    VisitRecord("롯데월드몰", "10/19 (일)", VisitMode.DROPOFF, "1번 게이트 승하차 구역에 내려줌", "맑음 · 11:20"),
    VisitRecord("현대백화점 판교", "10/12 (일)", VisitMode.PARK, "지하 4층 F구역 주차", "맑음 · 18:05"),
)

// ponytail: 가족 구성은 초대 기능 전까지 더미
val SAMPLE_FAMILY = listOf("엄마", "지민")
