package com.majung.vehicle.data.remote.dto

import kotlinx.serialization.Serializable

// packages/shared/src/index.ts 의 Visit 과 필드를 맞춘다
@Serializable
data class VisitDto(
    val id: String,
    val facilityId: String,
    val mode: String,
    val phase: String,
    val confirmedAccessPointId: String,
    val version: Int,
    val passengerReadiness: String,
    val updatedAt: String,
    val expiresAt: String,
    val parkingFloor: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class FacilityDto(val id: String, val name: String, val address: String, val lat: Double, val lng: Double, val accessPoints: List<AccessPointDto>, val dataStatus: String = "EXAMPLE")
@Serializable
data class AccessPointDto(val id: String, val name: String, val lat: Double, val lng: Double, val description: String, val kind: String, val allowed: Boolean, val walkMinutes: Int? = null, val covered: Boolean = false)
@Serializable
data class WeatherDto(val condition: String, val tempC: Int? = null, val rainChance: Int? = null, val rainSoon: Boolean, val summary: String, val checkedAt: String, val source: String)
@Serializable
data class RankedAccessPointDto(val accessPointId: String, val recommended: Boolean, val reason: String? = null)
/** C04 시설 날씨와 추천 순서. weather가 null이면 날씨 없이 걷는 시간 순 */
@Serializable
data class FacilityWeatherDto(val weather: WeatherDto? = null, val ranking: List<RankedAccessPointDto>)
@Serializable
data class MeetingProposalDto(val id: String, val visitId: String, val proposedBy: String, val accessPointId: String, val fromAccessPointId: String, val baseVersion: Int, val status: String, val reason: String? = null, val createdAt: String, val expiresAt: String)
@Serializable
data class VehicleStatusDto(val lat: Double? = null, val lng: Double? = null, val etaSec: Int? = null, val navResult: String? = null, val recordedAt: String)
@Serializable
data class PlaceRatingDto(val rating: Int, val tags: List<String>, val comment: String? = null)
@Serializable
data class VisitSnapshot(val visit: VisitDto, val role: String, val facility: FacilityDto, val proposals: List<MeetingProposalDto>, val vehicle: VehicleStatusDto? = null, val rating: PlaceRatingDto? = null)
@Serializable
data class VisitSession(val visit: VisitDto, val driverToken: String, val passengerToken: String)
@Serializable
data class InviteResult(val sentTo: String)
@Serializable
data class HistoryEntry(val visit: VisitDto, val facility: FacilityDto, val rating: PlaceRatingDto? = null)
