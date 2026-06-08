package com.mapt.demo.warehouse

import org.junit.Assert.assertEquals
import org.junit.Test

class WarehouseRegistryTest {

    @Test
    fun locations_are_ids_0_1_2() {
        assertEquals(setOf(0, 1, 2), WarehouseRegistry.locationIds())
    }

    @Test
    fun items_are_ids_3_4() {
        assertEquals(setOf(3, 4), WarehouseRegistry.itemIds())
    }

    @Test
    fun labels_match_poc() {
        assertEquals("Loc-A1", WarehouseRegistry.labelOf(0))
        assertEquals("Item-001", WarehouseRegistry.labelOf(3))
        assertEquals(null, WarehouseRegistry.labelOf(99))
    }
}
