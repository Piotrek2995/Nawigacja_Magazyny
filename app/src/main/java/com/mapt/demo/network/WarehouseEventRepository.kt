package com.mapt.demo.network

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WarehouseEventRepository(
    private val api: WarehouseApi = RetrofitProvider.warehouseApi
) {
    fun sendEvent(
        itemLabel: String,
        locationLabel: String,
        type: String,
        capturedAtEpochMs: Long,
        onSuccess: (WarehouseEventResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = WarehouseEventRequest(itemLabel, locationLabel, type, capturedAtEpochMs)
        api.recordEvent(request).enqueue(object : Callback<WarehouseEventResponse> {
            override fun onResponse(
                call: Call<WarehouseEventResponse>,
                response: Response<WarehouseEventResponse>
            ) {
                if (response.isSuccessful) {
                    onSuccess(response.body() ?: WarehouseEventResponse(message = "200 OK"))
                } else {
                    onError("HTTP ${response.code()}")
                }
            }

            override fun onFailure(call: Call<WarehouseEventResponse>, t: Throwable) {
                onError(t.message?.takeIf { it.isNotBlank() } ?: "Nieznany błąd połączenia")
            }
        })
    }
}
