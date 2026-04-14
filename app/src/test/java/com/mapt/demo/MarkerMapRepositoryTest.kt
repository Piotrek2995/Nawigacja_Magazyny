package com.mapt.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkerMapRepositoryTest {
    @Test
    fun marker23_should_have_expected_location_data() {
        val marker = MarkerMapRepository.markerMap[23]

        assertNotNull(marker)
        assertEquals("A3", marker?.location)
        assertEquals(10.5, marker?.x ?: 0.0, 0.0001)
        assertEquals(3.2, marker?.y ?: 0.0, 0.0001)
    }
}

