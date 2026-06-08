package com.mapt.demo.backend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarehouseCsvServiceTest {

    @Test
    fun append_writes_header_then_row_and_returns_count(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val service = WarehouseCsvService(csv.absolutePath)

        val count = service.append(
            WarehouseEventRequest("Item-001", "Loc-A1", "PICKED_UP", 0L)
        )

        assertEquals(1, count)
        val lines = csv.readLines()
        assertEquals("czas;iso;typ;towar;lokalizacja", lines[0])
        assertTrue(lines[1].endsWith("POBRANO;Item-001;Loc-A1"))
    }

    @Test
    fun second_append_does_not_duplicate_header(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val service = WarehouseCsvService(csv.absolutePath)

        service.append(WarehouseEventRequest("Item-001", "Loc-A1", "PICKED_UP", 0L))
        val count2 = service.append(WarehouseEventRequest("Item-001", "Loc-B2", "PUT_DOWN", 1000L))

        assertEquals(2, count2)
        val lines = csv.readLines()
        assertEquals(3, lines.size) // naglowek + 2 wiersze
        assertTrue(lines[2].endsWith("ZŁOŻONO;Item-001;Loc-B2"))
    }
}
