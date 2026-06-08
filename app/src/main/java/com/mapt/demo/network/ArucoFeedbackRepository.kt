package com.mapt.demo.network

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ArucoFeedbackRepository(
    private val api: ArucoFeedbackApi = RetrofitProvider.arucoFeedbackApi
) {
    fun sendGeometryFeedback(
        markerId: Int,
        location: String,
        worldX: Double,
        worldY: Double,
        distanceMeters: Double,
        cameraXInMarker: Double,
        cameraZInMarker: Double,
        onSuccess: (ArucoGeometryFeedbackResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = ArucoGeometryFeedbackRequest(
            markerId = markerId,
            location = location,
            worldX = worldX,
            worldY = worldY,
            distanceMeters = distanceMeters,
            cameraXInMarker = cameraXInMarker,
            cameraZInMarker = cameraZInMarker,
            capturedAtEpochMs = System.currentTimeMillis()
        )

        api.sendGeometryFeedback(request).enqueue(object : Callback<ArucoGeometryFeedbackResponse> {
            override fun onResponse(
                call: Call<ArucoGeometryFeedbackResponse>,
                response: Response<ArucoGeometryFeedbackResponse>
            ) {
                if (response.isSuccessful) {
                    onSuccess(response.body() ?: ArucoGeometryFeedbackResponse(message = "200 OK"))
                } else {
                    onError("HTTP ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ArucoGeometryFeedbackResponse>, t: Throwable) {
                val message = t.message?.takeIf { it.isNotBlank() } ?: "Nieznany błąd połączenia"
                onError(message)
            }
        })
    }
}
