package com.mapt.demo.network

import com.mapt.demo.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitProvider {
    val arucoFeedbackApi: ArucoFeedbackApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.ARUCO_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArucoFeedbackApi::class.java)
    }

    val warehouseApi: WarehouseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.ARUCO_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WarehouseApi::class.java)
    }
}
