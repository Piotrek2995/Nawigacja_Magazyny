package com.mapt.demo

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

object MarkerMapRepository {
    // ID markera -> pozycja markera w mapie i orientacja (yaw) względem osi mapy.
    val markerMap: Map<Int, MarkerMapEntry> = listOf(
        MarkerMapEntry(id = 2, location = "A3", x = 1e05, y = 3.2, yawDeg = 0.0),
        MarkerMapEntry(id = 42, location = "B1", x = 2.0, y = 7.8, yawDeg = 90.0)
    ).associateBy { it.id }
}

