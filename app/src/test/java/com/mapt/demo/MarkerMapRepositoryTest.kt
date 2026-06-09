package com.mapt.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkerMapRepositoryTest {
    @Test
    fun room_should_be_3_by_6_meters() {
        val room = MarkerMapRepository.roomConfig
        assertEquals(3.0, room.widthMeters, 0.0)
        assertEquals(6.0, room.heightMeters, 0.0)
    }

    @Test
    fun all_five_markers_id_0_to_4_should_exist_and_be_inside_room_bounds() {
        val room = MarkerMapRepository.roomConfig

        for (id in 0..4) {
            val marker = MarkerMapRepository.markerMap[id]
            assertNotNull("Brak markera o ID=$id", marker)
            assertTrue("Marker $id poza osią X", (marker?.x ?: -1.0) in 0.0..room.widthMeters)
            assertTrue("Marker $id poza osią Y", (marker?.y ?: -1.0) in 0.0..room.heightMeters)
        }
    }
}

