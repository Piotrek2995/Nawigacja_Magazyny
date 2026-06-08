package com.mapt.demo.warehouse

class WarehouseTracker(
    private val markers: Map<Int, WarehouseMarker> = WarehouseRegistry.markers,
    private val presenceTtlMs: Long = 800L,
    private val absenceTtlMs: Long = 1500L,
    private val maxRecentEvents: Int = 50
) {
    private var currentLocationLabel: String? = null
    private val itemLastSeenMs = mutableMapOf<Int, Long>()
    private val itemPresent = mutableMapOf<Int, Boolean>()
    private val recentEvents = ArrayDeque<WarehouseEvent>()

    private val itemMarkers = markers.values.filter { it.role == MarkerRole.ITEM }

    fun onFrame(detectedIds: Set<Int>, nowMs: Long): List<WarehouseEvent> {
        // 1) Aktualizuj biezaca lokalizacje z dowolnego widocznego markera lokalizacji.
        detectedIds.forEach { id ->
            val marker = markers[id] ?: return@forEach
            if (marker.role == MarkerRole.LOCATION) {
                currentLocationLabel = marker.label
            }
        }

        // 2) Odswiez czas ostatniego widzenia towarow.
        detectedIds.forEach { id ->
            if (markers[id]?.role == MarkerRole.ITEM) {
                itemLastSeenMs[id] = nowMs
            }
        }

        // 3) Ocen obecnosc kazdego znanego towaru z histereza i emituj zdarzenia.
        val emitted = mutableListOf<WarehouseEvent>()
        itemMarkers.forEach { item ->
            val lastSeen = itemLastSeenMs[item.id]
            val wasPresent = itemPresent[item.id] ?: false
            val isPresent = when {
                lastSeen == null -> false
                wasPresent -> (nowMs - lastSeen) <= absenceTtlMs
                else -> (nowMs - lastSeen) <= presenceTtlMs
            }

            if (isPresent && !wasPresent) {
                emitted += pushEvent(
                    WarehouseEvent(item.label, currentLocationLabel ?: "Loc-?", WarehouseEventType.PICKED_UP, nowMs)
                )
            } else if (!isPresent && wasPresent) {
                emitted += pushEvent(
                    WarehouseEvent(item.label, currentLocationLabel ?: "Loc-?", WarehouseEventType.PUT_DOWN, nowMs)
                )
            }
            itemPresent[item.id] = isPresent
        }
        return emitted
    }

    fun snapshot(nowMs: Long): WarehouseUiState {
        val present = itemMarkers
            .filter { itemPresent[it.id] == true }
            .map { it.label }
        return WarehouseUiState(
            currentLocation = currentLocationLabel,
            presentItems = present,
            recentEvents = recentEvents.toList(),
            status = currentLocationLabel?.let { "Strefa: $it" } ?: "Szukam markera lokalizacji..."
        )
    }

    private fun pushEvent(event: WarehouseEvent): WarehouseEvent {
        recentEvents.addFirst(event)
        while (recentEvents.size > maxRecentEvents) recentEvents.removeLast()
        return event
    }
}
