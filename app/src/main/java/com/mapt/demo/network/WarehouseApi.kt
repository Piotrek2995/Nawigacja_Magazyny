package com.mapt.demo.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface WarehouseApi {
    @POST("api/v1/warehouse/event")
    fun recordEvent(@Body request: WarehouseEventRequest): Call<WarehouseEventResponse>
}
