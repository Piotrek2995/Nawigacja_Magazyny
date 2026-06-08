package com.mapt.demo.backend

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@Service
class WarehouseCsvService(
    @Value("\${warehouse.csv.path:events.csv}") private val csvPath: String
) {
    private val lock = Any()
    private val counter = AtomicInteger(0)
    private val header = "czas;iso;typ;towar;lokalizacja"
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    fun append(request: WarehouseEventRequest): Int {
        synchronized(lock) {
            val file = File(csvPath)
            val isNew = !file.exists() || file.length() == 0L
            val instant = Instant.ofEpochMilli(request.capturedAtEpochMs)
            val typLabel = when (request.type) {
                "PICKED_UP" -> "POBRANO"
                "PUT_DOWN" -> "ZŁOŻONO"
                else -> request.type
            }
            val line = listOf(
                timeFmt.format(instant),
                isoFmt.format(instant),
                typLabel,
                request.itemLabel,
                request.locationLabel
            ).joinToString(";")
            file.appendText(buildString {
                if (isNew) {
                    append(header)
                    append("\n")
                }
                append(line)
                append("\n")
            })
            return counter.incrementAndGet()
        }
    }

    fun count(): Int = counter.get()
}
