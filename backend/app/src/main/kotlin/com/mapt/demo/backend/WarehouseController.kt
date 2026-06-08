package com.mapt.demo.backend

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentLinkedDeque

@RestController
@RequestMapping("/api/v1/warehouse")
class WarehouseController(
    private val csvService: WarehouseCsvService
) {
    private val recent = ConcurrentLinkedDeque<WarehouseEventRequest>()

    @PostMapping("/event")
    fun recordEvent(@RequestBody request: WarehouseEventRequest): WarehouseEventResponse {
        val total = csvService.append(request)
        recent.addFirst(request)
        while (recent.size > 100) recent.removeLast()
        return WarehouseEventResponse(
            accepted = true,
            message = "Zapisano ${request.type} ${request.itemLabel} @ ${request.locationLabel}",
            totalEvents = total
        )
    }

    @GetMapping("/log")
    fun log(): List<WarehouseEventRequest> = recent.toList()
}
