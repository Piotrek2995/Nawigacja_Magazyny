package com.mapt.demo.warehouse

enum class MarkerRole { LOCATION, ITEM }

data class WarehouseMarker(
    val id: Int,
    val role: MarkerRole,
    val label: String
)

enum class WarehouseEventType { PICKED_UP, PUT_DOWN }

data class WarehouseEvent(
    val itemLabel: String,
    val locationLabel: String,
    val type: WarehouseEventType,
    val epochMs: Long
)

data class WarehouseUiState(
    val currentLocation: String? = null,
    val presentItems: List<String> = emptyList(),
    val recentEvents: List<WarehouseEvent> = emptyList(),
    val status: String = "Szukam markerów..."
)

object WarehouseRegistry {
    val markers: Map<Int, WarehouseMarker> = listOf(
        WarehouseMarker(0, MarkerRole.LOCATION, "Loc-A1"),
        WarehouseMarker(1, MarkerRole.LOCATION, "Loc-B2"),
        WarehouseMarker(2, MarkerRole.LOCATION, "Loc-C3"),
        WarehouseMarker(3, MarkerRole.ITEM, "Item-001"),
        WarehouseMarker(4, MarkerRole.ITEM, "Item-002")
    ).associateBy { it.id }

    fun roleOf(id: Int): MarkerRole? = markers[id]?.role
    fun labelOf(id: Int): String? = markers[id]?.label
    fun locationIds(): Set<Int> =
        markers.values.filter { it.role == MarkerRole.LOCATION }.map { it.id }.toSet()
    fun itemIds(): Set<Int> =
        markers.values.filter { it.role == MarkerRole.ITEM }.map { it.id }.toSet()
}
