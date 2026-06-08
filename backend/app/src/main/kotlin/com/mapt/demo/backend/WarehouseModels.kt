package com.mapt.demo.backend

data class WarehouseEventRequest(
    val itemLabel: String,
    val locationLabel: String,
    val type: String,            // "PICKED_UP" lub "PUT_DOWN"
    val capturedAtEpochMs: Long
)

data class WarehouseEventResponse(
    val accepted: Boolean,
    val message: String,
    val totalEvents: Int
)
