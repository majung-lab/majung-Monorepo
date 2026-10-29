package com.majung.vehicle.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test
    fun distanceWestToEast() = assertEquals(458.0, distanceM(mapPoint("서문 로비").pos, mapPoint("동문 로비").pos), 10.0)

    @Test
    fun nearestSnapsOnlyInsideRadius() {
        val west = mapPoint("서문 로비").pos
        val near = nearest(LatLng(west.lat + 0.0002, west.lng))
        assertEquals("west", near.point.id)
        assertTrue(near.snapped)
        assertFalse(nearest(LatLng(west.lat, west.lng - 0.002)).snapped)
    }
}
