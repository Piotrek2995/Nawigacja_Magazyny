package com.mapt.demo.network

data class ArucoGeometryFeedbackRequest(
    val markerId: Int,
    val location: String,
    val worldX: Double,
    val worldY: Double,
    val distanceMeters: Double,
    val cameraXInMarker: Double,
    val cameraZInMarker: Double,
    val capturedAtEpochMs: Long
)

data class ArucoGeometryFeedbackResponse(
    val accepted: Boolean? = null,
    val message: String? = null,
    val qualityScore: Double? = null,
    val correctedX: Double? = null,
    val correctedY: Double? = null
)
