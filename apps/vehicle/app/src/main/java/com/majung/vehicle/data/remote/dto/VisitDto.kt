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
)
