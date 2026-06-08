package com.mapt.demo.warehouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarehouseTrackerTest {

    private fun tracker() = WarehouseTracker() // domyslnie presence=800ms, absence=1500ms

    @Test
    fun appearing_item_emits_picked_up_with_current_location() {
        val t = tracker()
        assertTrue(t.onFrame(setOf(0), 0L).isEmpty())          // Loc-A1, brak towaru
        val events = t.onFrame(setOf(0, 3), 100L)              // Item-001 pojawia sie
        assertEquals(1, events.size)
        assertEquals(WarehouseEventType.PICKED_UP, events[0].type)
        assertEquals("Item-001", events[0].itemLabel)
        assertEquals("Loc-A1", events[0].locationLabel)
    }

    @Test
    fun disappearing_item_emits_put_down_after_absence() {
        val t = tracker()
        t.onFrame(setOf(0, 3), 0L)                             // POBRANO
        val none = t.onFrame(emptySet(), 1000L)                // wciaz obecny (<1500ms)
        assertTrue(none.isEmpty())
        val events = t.onFrame(emptySet(), 2000L)              // 2000ms > 1500ms -> ZLOZONO
        assertEquals(1, events.size)
        assertEquals(WarehouseEventType.PUT_DOWN, events[0].type)
        assertEquals("Item-001", events[0].itemLabel)
        assertEquals("Loc-A1", events[0].locationLabel)
    }

    @Test
    fun flicker_does_not_emit_extra_events() {
        val t = tracker()
        t.onFrame(setOf(0, 3), 0L)                             // POBRANO
        assertTrue(t.onFrame(emptySet(), 800L).isEmpty())      // chwilowa utrata < 1500ms
        assertTrue(t.onFrame(setOf(3), 900L).isEmpty())        // znow widoczny, brak zdarzenia
        assertTrue(t.onFrame(setOf(3), 1500L).isEmpty())       // wciaz obecny
    }

    @Test
    fun location_change_updates_zone_of_next_event() {
        val t = tracker()
        t.onFrame(setOf(0, 3), 0L)                             // POBRANO @ Loc-A1
        t.onFrame(setOf(1, 3), 100L)                           // strefa zmienia sie na Loc-B2
        val events = t.onFrame(emptySet(), 2000L)              // ZLOZONO @ Loc-B2
        assertEquals(1, events.size)
        assertEquals(WarehouseEventType.PUT_DOWN, events[0].type)
        assertEquals("Loc-B2", events[0].locationLabel)
    }

    @Test
    fun item_seen_before_any_location_uses_placeholder() {
        val t = tracker()
        val events = t.onFrame(setOf(3), 0L)                   // brak markera lokalizacji
        assertEquals(1, events.size)
        assertEquals("Loc-?", events[0].locationLabel)
    }

    @Test
    fun snapshot_reports_location_and_present_items() {
        val t = tracker()
        t.onFrame(setOf(1, 4), 0L)                             // Loc-B2 + Item-002
        val state = t.snapshot(0L)
        assertEquals("Loc-B2", state.currentLocation)
        assertTrue(state.presentItems.contains("Item-002"))
    }

    @Test
    fun full_poc_scenario_records_ordered_events() {
        val t = tracker()
        t.onFrame(setOf(0), 0L)                                // wozek w A1
        t.onFrame(setOf(0, 3), 100L)                           // POBRANO Item-001 @ A1
        t.onFrame(setOf(1, 3), 5000L)                          // przejazd do B2 (towar wciaz obecny)
        t.onFrame(setOf(1), 5100L)                             // towar zdjety z wozka
        t.onFrame(emptySet(), 7000L)                           // ZLOZONO Item-001 @ B2
        t.onFrame(setOf(2, 4), 8000L)                          // POBRANO Item-002 @ C3

        val log = t.snapshot(8000L).recentEvents               // najnowsze pierwsze
        assertEquals(3, log.size)
        assertEquals(WarehouseEventType.PICKED_UP, log[0].type)
        assertEquals("Item-002", log[0].itemLabel)
        assertEquals("Loc-C3", log[0].locationLabel)
        assertEquals(WarehouseEventType.PUT_DOWN, log[1].type)
        assertEquals("Loc-B2", log[1].locationLabel)
        assertEquals(WarehouseEventType.PICKED_UP, log[2].type)
        assertEquals("Loc-A1", log[2].locationLabel)
    }
}
