package com.mapt.demo

import kotlin.random.Random

data class MarkerMapEntry(
    val id: Int,
    val location: String,
    val x: Double,
    val y: Double,
    val yawDeg: Double = 0.0
)

data class PoseUiState(
    val markerId: Int? = null,
    val location: String = "Brak detekcji",
    val worldX: Double? = null,
    val worldY: Double? = null,
    val distanceMeters: Double? = null,
    val cameraXInMarker: Double? = null,
    val cameraZInMarker: Double? = null,
    val status: String = "Szukam markera ArUco..."
)

data class RoomMapConfig(
    val widthMeters: Double,
    val heightMeters: Double
)

object MarkerMapRepository {
    val roomConfig = RoomMapConfig(widthMeters = 12.0, heightMeters = 8.0)

    private val marker2RandomPosition = MarkerMapEntry(
        id = 2,
        location = "A4",
        x = Random.nextDouble(from = 0.8, until = roomConfig.widthMeters - 0.8),
        y = Random.nextDouble(from = 0.8, until = roomConfig.heightMeters - 0.8),
        yawDeg = 0.0
    )

    // ID markera -> pozycja markera w mapie i orientacja (yaw) względem osi mapy.
    val markerMap: Map<Int, MarkerMapEntry> = listOf(
        marker2RandomPosition,
        MarkerMapEntry(id = 42, location = "B1", x = 2.0, y = 7.8, yawDeg = 90.0)
    ).associateBy { it.id }
}

