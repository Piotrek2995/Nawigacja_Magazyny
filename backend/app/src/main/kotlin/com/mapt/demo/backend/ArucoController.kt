package com.mapt.demo.backend

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/aruco")
class ArucoController {

    @PostMapping("/geometry-feedback")
    fun sendGeometryFeedback(@RequestBody request: ArucoGeometryFeedbackRequest): ArucoGeometryFeedbackResponse {
        // Symulacja przetwarzania - w rzeczywistości można dodać logikę
        return ArucoGeometryFeedbackResponse(
            accepted = true,
            message = "Feedback received for marker ${request.markerId}",
            qualityScore = 0.95,
            correctedX = request.worldX + 0.1,
            correctedY = request.worldY - 0.05
        )
    }
}

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
