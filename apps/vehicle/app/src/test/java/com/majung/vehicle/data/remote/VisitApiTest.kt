package com.majung.vehicle.data.remote

import com.majung.vehicle.data.remote.dto.VisitSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VisitApiTest {
    @Test fun snapshotKeepsProposalOriginAndMissingTelemetry() {
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString<VisitSnapshot>("""{
          "visit":{"id":"v","facilityId":"1","mode":"PICKUP","phase":"MOVING","confirmedAccessPointId":"2","version":4,"passengerReadiness":"READY","updatedAt":"now","expiresAt":"later"},
          "role":"DRIVER","facility":{"id":"1","name":"예시 시설","address":"주소","lat":37.5,"lng":127.2,"dataStatus":"EXAMPLE","accessPoints":[]},
          "proposals":[{"id":"p","visitId":"v","proposedBy":"PASSENGER","accessPointId":"2","fromAccessPointId":"1","baseVersion":3,"status":"ACCEPTED","createdAt":"now","expiresAt":"later"}],"vehicle":null,"rating":null
        }""")
        assertEquals("1", snapshot.proposals.single().fromAccessPointId)
        assertEquals("2", snapshot.visit.confirmedAccessPointId)
        assertNull(snapshot.vehicle)
        assertEquals("READY", snapshot.visit.passengerReadiness)
    }
}
