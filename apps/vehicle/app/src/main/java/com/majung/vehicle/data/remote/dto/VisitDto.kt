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
data class AccessPointDto(val id: String, val name: String, val lat: Double, val lng: Double, val description: String, val kind: String, val allowed: Boolean)
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
data class HistoryEntry(val visit: VisitDto, val facility: FacilityDto, val rating: PlaceRatingDto? = null)
