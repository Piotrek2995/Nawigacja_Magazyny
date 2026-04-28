package com.mapt.demo

import android.app.Application
import org.maplibre.android.MapLibre

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
