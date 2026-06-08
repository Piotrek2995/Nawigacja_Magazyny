package com.mapt.demo.network

data class WarehouseEventRequest(
    val itemLabel: String,
    val locationLabel: String,
    val type: String,
    val capturedAtEpochMs: Long
)

data class WarehouseEventResponse(
    val accepted: Boolean? = null,
    val message: String? = null,
    val totalEvents: Int? = null
)
