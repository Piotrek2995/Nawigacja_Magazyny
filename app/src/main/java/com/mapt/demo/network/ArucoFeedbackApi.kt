package com.mapt.demo.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ArucoFeedbackApi {
    @POST("api/v1/aruco/geometry-feedback")
    fun sendGeometryFeedback(
        @Body request: ArucoGeometryFeedbackRequest
    ): Call<ArucoGeometryFeedbackResponse>
}
