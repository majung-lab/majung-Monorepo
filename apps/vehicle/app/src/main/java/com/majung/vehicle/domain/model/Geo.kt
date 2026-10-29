package com.majung.vehicle.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** 승하차 지점 좌표. 웹(apps/web/src/data/dummy.ts)과 같은 더미 값, 대략 좌표(실측 아님) */
data class MapPoint(val id: String, val name: String, val pos: LatLng)

val FACILITY_POS = LatLng(37.5455, 127.224)

// ponytail: 시설 데이터(data/facilities) 붙기 전까지 더미
val MAP_POINTS = listOf(
    MapPoint("north", "북문 로비", LatLng(37.5468, 127.2239)),
    MapPoint("west", "서문 로비", LatLng(37.5455, 127.2214)),
    MapPoint("east", "동문 로비", LatLng(37.5455, 127.2266)),
    MapPoint("b1", "B1 승하차장", LatLng(37.5446, 127.225)),
    MapPoint("front", "정문 앞 도로", LatLng(37.5441, 127.2236)), // 차량 C04 후보 (웹 더미엔 없음)
)

fun mapPoint(name: String): MapPoint = MAP_POINTS.firstOrNull { it.name == name } ?: MAP_POINTS.first()

/** 두 좌표 사이 거리 (m), haversine */
fun distanceM(a: LatLng, b: LatLng): Double {
    fun rad(d: Double) = Math.toRadians(d)
    val h = sin(rad(b.lat - a.lat) / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(rad(b.lng - a.lng) / 2).pow(2)
    return 2 * 6_371_000 * asin(sqrt(h))
}

/** 확인된 승하차 지점 반경. 밖이면 "지도에서 고른 위치" */
const val SNAP_RADIUS_M = 60.0

data class Nearest(val point: MapPoint, val distanceM: Double) {
    val snapped get() = distanceM <= SNAP_RADIUS_M
}

fun nearest(at: LatLng, points: List<MapPoint> = MAP_POINTS): Nearest =
    points.map { Nearest(it, distanceM(it.pos, at)) }.minBy { it.distanceM }
