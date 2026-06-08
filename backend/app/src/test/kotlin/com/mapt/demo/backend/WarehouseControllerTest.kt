package com.mapt.demo.backend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarehouseControllerTest {

    @Test
    fun recordEvent_persists_row_and_returns_total(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val controller = WarehouseController(WarehouseCsvService(csv.absolutePath))

        val response = controller.recordEvent(
            WarehouseEventRequest("Item-002", "Loc-C3", "PICKED_UP", 0L)
        )

        assertTrue(response.accepted)
        assertEquals(1, response.totalEvents)
        assertTrue(csv.readLines()[1].endsWith("POBRANO;Item-002;Loc-C3"))
    }

    @Test
    fun log_returns_recent_events_newest_first(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val controller = WarehouseController(WarehouseCsvService(csv.absolutePath))

        controller.recordEvent(WarehouseEventRequest("Item-001", "Loc-A1", "PICKED_UP", 0L))
        controller.recordEvent(WarehouseEventRequest("Item-001", "Loc-B2", "PUT_DOWN", 1000L))

        val log = controller.log()
        assertEquals(2, log.size)
        assertEquals("PUT_DOWN", log[0].type)     // najnowsze pierwsze
        assertEquals("PICKED_UP", log[1].type)    // starsze drugie
    }
}
