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

    /**
     * Przetwarza jedną klatkę. UWAGA: limity czasowe obecności są oceniane wyłącznie
     * w momencie wywołania — aby zdarzenie ZŁOŻONO (PUT_DOWN) na pewno się pojawiło,
     * trzeba wywołać onFrame przynajmniej raz po upływie absenceTtlMs. Pętla kamery
     * ~30 fps spełnia ten warunek w normalnej pracy.
     */
    fun onFrame(detectedIds: Set<Int>, nowMs: Long): List<WarehouseEvent> {
        // 1) Aktualizuj biezaca lokalizacje i czas widzenia towarow jednym przejsciem.
        detectedIds.forEach { id ->
            when (markers[id]?.role) {
                MarkerRole.LOCATION -> currentLocationLabel = markers[id]?.label
                MarkerRole.ITEM -> itemLastSeenMs[id] = nowMs
                null -> Unit
            }
        }

        // 2) Ocen obecnosc kazdego znanego towaru z histereza i emituj zdarzenia.
        val emitted = mutableListOf<WarehouseEvent>()
        itemMarkers.forEach { item ->
            val wasPresent = itemPresent[item.id] ?: false
            val isPresent = isItemPresent(item.id, nowMs)

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
        // Obecnosc liczona wzgledem nowMs, by podglad nie pokazywal nieaktualnych
        // towarow, gdy onFrame nie byl wolany od jakiegos czasu (np. pauza kamery).
        val present = itemMarkers
            .filter { isItemPresent(it.id, nowMs) }
            .map { it.label }
        return WarehouseUiState(
            currentLocation = currentLocationLabel,
            presentItems = present,
            recentEvents = recentEvents.toList(),
            status = currentLocationLabel?.let { "Strefa: $it" } ?: "Szukam markera lokalizacji..."
        )
    }

    /**
     * Histereza obecnosci: towar pozostaje obecny dopoki nie minie absenceTtlMs od
     * ostatniego widzenia; nieobecny staje sie obecny, gdy widziano go w presenceTtlMs.
     */
    private fun isItemPresent(itemId: Int, nowMs: Long): Boolean {
        val lastSeen = itemLastSeenMs[itemId] ?: return false
        val wasPresent = itemPresent[itemId] ?: false
        return if (wasPresent) {
            (nowMs - lastSeen) <= absenceTtlMs
        } else {
            (nowMs - lastSeen) <= presenceTtlMs
        }
    }

    private fun pushEvent(event: WarehouseEvent): WarehouseEvent {
        recentEvents.addFirst(event)
        while (recentEvents.size > maxRecentEvents) recentEvents.removeLast()
        return event
    }
}
