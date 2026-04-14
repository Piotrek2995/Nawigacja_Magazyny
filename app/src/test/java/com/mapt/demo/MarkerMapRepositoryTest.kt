package com.mapt.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkerMapRepositoryTest {
    @Test
    fun marker2_should_exist_and_be_inside_room_bounds() {
        val marker = MarkerMapRepository.markerMap[2]
        val room = MarkerMapRepository.roomConfig

        assertNotNull(marker)
        assertEquals("A4", marker?.location)
        assertTrue((marker?.x ?: 0.0) in 0.8..(room.widthMeters - 0.8))
        assertTrue((marker?.y ?: 0.0) in 0.8..(room.heightMeters - 0.8))
    }
}

