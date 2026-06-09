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

data class RoomMapConfig(
    val widthMeters: Double,
    val heightMeters: Double
)

object MarkerMapRepository {
    // Pomieszczenie 3 m (szerokość, oś X) x 6 m (długość, oś Y).
    val roomConfig = RoomMapConfig(widthMeters = 3.0, heightMeters = 6.0)

    // ID markera -> pozycja markera w mapie i orientacja (yaw) względem osi mapy.
    // 5 markerów ArUco (ID 0-4) rozmieszczonych wzdłuż ścian pomieszczenia 3 x 6 m.
    val markerMap: Map<Int, MarkerMapEntry> = listOf(
        MarkerMapEntry(id = 0, location = "A1", x = 0.5, y = 0.5, yawDeg = 0.0),
        MarkerMapEntry(id = 1, location = "A2", x = 2.5, y = 0.5, yawDeg = 0.0),
        MarkerMapEntry(id = 2, location = "B1", x = 1.5, y = 3.0, yawDeg = 0.0),
        MarkerMapEntry(id = 3, location = "C1", x = 0.5, y = 5.5, yawDeg = 0.0),
        MarkerMapEntry(id = 4, location = "C2", x = 2.5, y = 5.5, yawDeg = 0.0)
    ).associateBy { it.id }
}

