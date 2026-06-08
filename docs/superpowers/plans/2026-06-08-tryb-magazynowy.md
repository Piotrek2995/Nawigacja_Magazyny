# Tryb magazynowy (PoC rotacji towaru) — plan implementacji

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dodać do aplikacji drugi tryb („Magazyn”), który wykrywa markery ArUco lokalizacji i towaru w jednej klatce kamery, rejestruje zdarzenia POBRANO/ZŁOŻONO z czasem i zapisuje je przez backend do pliku CSV — zgodnie z PoC „Optymalizacja rozmieszczenia asortymentu”.

**Architecture:** Czysta maszyna stanów `WarehouseTracker` (testowalna na JVM) konsumuje zbiór wykrytych ID markerów na klatkę i emituje zdarzenia. `ArucoEngine` dostaje metodę zwracającą wszystkie ID. Warstwa sieci wysyła zdarzenia do nowego endpointu Spring Boot, który dopisuje je do `events.csv`. UI Compose dostaje przełącznik trybu; tryb magazynowy pokazuje podgląd kamery + listę zdarzeń na żywo (bez mapy MapLibre). Istniejący tryb nawigacji pozostaje nietknięty.

**Tech Stack:** Kotlin, Jetpack Compose, OpenCV (ArUco), Retrofit/Gson, Spring Boot 3 (Kotlin), JUnit4 (app), JUnit5/kotlin-test (backend).

**Spec:** `docs/superpowers/specs/2026-06-08-tryb-magazynowy-design.md`

---

## Struktura plików

**Backend (`backend/app/src/main/kotlin/com/mapt/demo/backend/`):**
- Create `WarehouseModels.kt` — `WarehouseEventRequest`, `WarehouseEventResponse`.
- Create `WarehouseCsvService.kt` — thread-safe zapis do CSV.
- Create `WarehouseController.kt` — endpointy `POST /event`, `GET /log`.
- Delete `backend/app/src/test/kotlin/com/mapt/demo/backend/AppTest.kt` — wadliwy szablon (nie kompiluje się).
- Create `backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseCsvServiceTest.kt`.
- Create `backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseControllerTest.kt`.

**Android (`app/src/main/java/com/mapt/demo/`):**
- Create `warehouse/WarehouseModels.kt` — role, marker, zdarzenie, stan UI, rejestr.
- Create `warehouse/WarehouseTracker.kt` — maszyna stanów.
- Create `app/src/test/java/com/mapt/demo/warehouse/WarehouseRegistryTest.kt`.
- Create `app/src/test/java/com/mapt/demo/warehouse/WarehouseTrackerTest.kt`.
- Modify `ArucoEngine.kt` — dodać `detectMarkerIds(rgba): Set<Int>`.
- Create `network/WarehouseEventModels.kt`, `network/WarehouseApi.kt`, `network/WarehouseEventRepository.kt`.
- Modify `network/RetrofitProvider.kt` — dodać `warehouseApi`.
- Create `ui/CameraPreview.kt` — współdzielony podgląd kamery z callbackiem na klatkę.
- Create `warehouse/WarehouseScreen.kt` — ekran trybu magazynowego.
- Modify `MainActivity.kt` — przełącznik trybu + podpięcie wysyłki zdarzeń.

**Dokumentacja:**
- Modify `README.md` — sekcja „Tryb magazynowy” + instrukcja testu PoC.

---

## Task 1: Backend — modele zdarzeń magazynowych

**Files:**
- Create: `backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseModels.kt`

- [ ] **Step 1: Utwórz modele**

```kotlin
package com.mapt.demo.backend

data class WarehouseEventRequest(
    val itemLabel: String,
    val locationLabel: String,
    val type: String,            // "PICKED_UP" lub "PUT_DOWN"
    val capturedAtEpochMs: Long
)

data class WarehouseEventResponse(
    val accepted: Boolean,
    val message: String,
    val totalEvents: Int
)
```

- [ ] **Step 2: Commit**

```bash
git add backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseModels.kt
git commit -m "feat(backend): modele zdarzen magazynowych"
```

---

## Task 2: Backend — serwis zapisu CSV (TDD)

**Files:**
- Delete: `backend/app/src/test/kotlin/com/mapt/demo/backend/AppTest.kt`
- Create: `backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseCsvServiceTest.kt`
- Create: `backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseCsvService.kt`

- [ ] **Step 1: Usuń wadliwy test szablonowy**

Plik `AppTest.kt` odwołuje się do `App().greeting`, które nie istnieje (jest tylko `DemoBackendApplication`), przez co cały test source set się nie kompiluje. Usuń go:

```bash
git rm backend/app/src/test/kotlin/com/mapt/demo/backend/AppTest.kt
```

- [ ] **Step 2: Napisz failing test serwisu CSV**

Utwórz `backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseCsvServiceTest.kt`:

```kotlin
package com.mapt.demo.backend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarehouseCsvServiceTest {

    @Test
    fun append_writes_header_then_row_and_returns_count(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val service = WarehouseCsvService(csv.absolutePath)

        val count = service.append(
            WarehouseEventRequest("Item-001", "Loc-A1", "PICKED_UP", 0L)
        )

        assertEquals(1, count)
        val lines = csv.readLines()
        assertEquals("czas;iso;typ;towar;lokalizacja", lines[0])
        assertTrue(lines[1].endsWith("POBRANO;Item-001;Loc-A1"))
    }

    @Test
    fun second_append_does_not_duplicate_header(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val service = WarehouseCsvService(csv.absolutePath)

        service.append(WarehouseEventRequest("Item-001", "Loc-A1", "PICKED_UP", 0L))
        val count2 = service.append(WarehouseEventRequest("Item-001", "Loc-B2", "PUT_DOWN", 1000L))

        assertEquals(2, count2)
        val lines = csv.readLines()
        assertEquals(3, lines.size) // nagłówek + 2 wiersze
        assertTrue(lines[2].endsWith("ZŁOŻONO;Item-001;Loc-B2"))
    }
}
```

- [ ] **Step 3: Uruchom test — ma się NIE skompilować/NIE przejść**

Run: `cd backend; .\gradlew.bat :app:test --tests "com.mapt.demo.backend.WarehouseCsvServiceTest"`
Expected: FAIL — `WarehouseCsvService` nie istnieje (błąd kompilacji).

- [ ] **Step 4: Zaimplementuj serwis CSV**

Utwórz `backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseCsvService.kt`:

```kotlin
package com.mapt.demo.backend

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

@Service
class WarehouseCsvService(
    @Value("\${warehouse.csv.path:events.csv}") private val csvPath: String
) {
    private val lock = Any()
    private val counter = AtomicInteger(0)
    private val header = "czas;iso;typ;towar;lokalizacja"
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    fun append(request: WarehouseEventRequest): Int {
        synchronized(lock) {
            val file = File(csvPath)
            val isNew = !file.exists() || file.length() == 0L
            val instant = Instant.ofEpochMilli(request.capturedAtEpochMs)
            val typLabel = when (request.type) {
                "PICKED_UP" -> "POBRANO"
                "PUT_DOWN" -> "ZŁOŻONO"
                else -> request.type
            }
            val line = listOf(
                timeFmt.format(instant),
                isoFmt.format(instant),
                typLabel,
                request.itemLabel,
                request.locationLabel
            ).joinToString(";")
            file.appendText(buildString {
                if (isNew) {
                    append(header)
                    append("\n")
                }
                append(line)
                append("\n")
            })
            return counter.incrementAndGet()
        }
    }

    fun count(): Int = counter.get()
}
```

- [ ] **Step 5: Uruchom test — ma przejść**

Run: `cd backend; .\gradlew.bat :app:test --tests "com.mapt.demo.backend.WarehouseCsvServiceTest"`
Expected: PASS (2 testy).

- [ ] **Step 6: Commit**

Usunięcie `AppTest.kt` jest już zastage'owane z Kroku 1 — zostanie zawarte w tym commicie.

```bash
git add backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseCsvService.kt backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseCsvServiceTest.kt
git commit -m "feat(backend): serwis zapisu zdarzen do CSV + usuniecie wadliwego AppTest"
```

---

## Task 3: Backend — kontroler zdarzeń magazynowych (TDD)

**Files:**
- Create: `backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseControllerTest.kt`
- Create: `backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseController.kt`

- [ ] **Step 1: Napisz failing test kontrolera**

Utwórz `backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseControllerTest.kt`:

```kotlin
package com.mapt.demo.backend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarehouseControllerTest {

    @Test
    fun recordEvent_persists_row_and_returns_total(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val controller = WarehouseController(WarehouseCsvService(csv.absolutePath))

        val response = controller.recordEvent(
            WarehouseEventRequest("Item-002", "Loc-C3", "PICKED_UP", 0L)
        )

        assertTrue(response.accepted)
        assertEquals(1, response.totalEvents)
        assertTrue(csv.readLines()[1].endsWith("POBRANO;Item-002;Loc-C3"))
    }

    @Test
    fun log_returns_recent_events_newest_first(@TempDir tmp: Path) {
        val csv = File(tmp.toFile(), "events.csv")
        val controller = WarehouseController(WarehouseCsvService(csv.absolutePath))

        controller.recordEvent(WarehouseEventRequest("Item-001", "Loc-A1", "PICKED_UP", 0L))
        controller.recordEvent(WarehouseEventRequest("Item-001", "Loc-B2", "PUT_DOWN", 1000L))

        val log = controller.log()
        assertEquals(2, log.size)
        assertEquals("PUT_DOWN", log[0].type)   // najnowsze pierwsze
    }
}
```

- [ ] **Step 2: Uruchom test — ma się NIE skompilować**

Run: `cd backend; .\gradlew.bat :app:test --tests "com.mapt.demo.backend.WarehouseControllerTest"`
Expected: FAIL — `WarehouseController` nie istnieje.

- [ ] **Step 3: Zaimplementuj kontroler**

Utwórz `backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseController.kt`:

```kotlin
package com.mapt.demo.backend

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentLinkedDeque

@RestController
@RequestMapping("/api/v1/warehouse")
class WarehouseController(
    private val csvService: WarehouseCsvService
) {
    private val recent = ConcurrentLinkedDeque<WarehouseEventRequest>()

    @PostMapping("/event")
    fun recordEvent(@RequestBody request: WarehouseEventRequest): WarehouseEventResponse {
        val total = csvService.append(request)
        recent.addFirst(request)
        while (recent.size > 100) recent.removeLast()
        return WarehouseEventResponse(
            accepted = true,
            message = "Zapisano ${request.type} ${request.itemLabel} @ ${request.locationLabel}",
            totalEvents = total
        )
    }

    @GetMapping("/log")
    fun log(): List<WarehouseEventRequest> = recent.toList()
}
```

- [ ] **Step 4: Uruchom test — ma przejść**

Run: `cd backend; .\gradlew.bat :app:test --tests "com.mapt.demo.backend.WarehouseControllerTest"`
Expected: PASS (2 testy).

- [ ] **Step 5: Uruchom pełny zestaw testów backendu**

Run: `cd backend; .\gradlew.bat :app:test`
Expected: PASS (cały backend kompiluje się i przechodzi).

- [ ] **Step 6: Commit**

```bash
git add backend/app/src/main/kotlin/com/mapt/demo/backend/WarehouseController.kt backend/app/src/test/kotlin/com/mapt/demo/backend/WarehouseControllerTest.kt
git commit -m "feat(backend): endpoint POST/GET zdarzen magazynowych"
```

---

## Task 4: Android — modele i rejestr markerów (TDD)

**Files:**
- Create: `app/src/main/java/com/mapt/demo/warehouse/WarehouseModels.kt`
- Create: `app/src/test/java/com/mapt/demo/warehouse/WarehouseRegistryTest.kt`

- [ ] **Step 1: Napisz failing test rejestru**

Utwórz `app/src/test/java/com/mapt/demo/warehouse/WarehouseRegistryTest.kt`:

```kotlin
package com.mapt.demo.warehouse

import org.junit.Assert.assertEquals
import org.junit.Test

class WarehouseRegistryTest {

    @Test
    fun locations_are_ids_0_1_2() {
        assertEquals(setOf(0, 1, 2), WarehouseRegistry.locationIds())
    }

    @Test
    fun items_are_ids_3_4() {
        assertEquals(setOf(3, 4), WarehouseRegistry.itemIds())
    }

    @Test
    fun labels_match_poc() {
        assertEquals("Loc-A1", WarehouseRegistry.labelOf(0))
        assertEquals("Item-001", WarehouseRegistry.labelOf(3))
        assertEquals(null, WarehouseRegistry.labelOf(99))
    }
}
```

- [ ] **Step 2: Uruchom test — ma się NIE skompilować**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.mapt.demo.warehouse.WarehouseRegistryTest"`
Expected: FAIL — `WarehouseRegistry` nie istnieje.

- [ ] **Step 3: Zaimplementuj modele i rejestr**

Utwórz `app/src/main/java/com/mapt/demo/warehouse/WarehouseModels.kt`:

```kotlin
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
```

- [ ] **Step 4: Uruchom test — ma przejść**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.mapt.demo.warehouse.WarehouseRegistryTest"`
Expected: PASS (3 testy).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mapt/demo/warehouse/WarehouseModels.kt app/src/test/java/com/mapt/demo/warehouse/WarehouseRegistryTest.kt
git commit -m "feat(app): modele i rejestr markerow magazynowych"
```

---

## Task 5: Android — maszyna stanów WarehouseTracker (TDD)

To jest serce logiki PoC. Pisz testy przed implementacją. Progi: obecność 800 ms, nieobecność 1500 ms.

**Files:**
- Create: `app/src/test/java/com/mapt/demo/warehouse/WarehouseTrackerTest.kt`
- Create: `app/src/main/java/com/mapt/demo/warehouse/WarehouseTracker.kt`

- [ ] **Step 1: Napisz failing testy trackera**

Utwórz `app/src/test/java/com/mapt/demo/warehouse/WarehouseTrackerTest.kt`:

```kotlin
package com.mapt.demo.warehouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarehouseTrackerTest {

    private fun tracker() = WarehouseTracker() // domyślnie presence=800ms, absence=1500ms

    @Test
    fun appearing_item_emits_picked_up_with_current_location() {
        val t = tracker()
        assertTrue(t.onFrame(setOf(0), 0L).isEmpty())          // Loc-A1, brak towaru
        val events = t.onFrame(setOf(0, 3), 100L)              // Item-001 pojawia się
        assertEquals(1, events.size)
        assertEquals(WarehouseEventType.PICKED_UP, events[0].type)
        assertEquals("Item-001", events[0].itemLabel)
        assertEquals("Loc-A1", events[0].locationLabel)
    }

    @Test
    fun disappearing_item_emits_put_down_after_absence() {
        val t = tracker()
        t.onFrame(setOf(0, 3), 0L)                             // POBRANO
        val none = t.onFrame(emptySet(), 1000L)                // wciąż obecny (<1500ms)
        assertTrue(none.isEmpty())
        val events = t.onFrame(emptySet(), 2000L)              // 2000ms > 1500ms -> ZŁOŻONO
        assertEquals(1, events.size)
        assertEquals(WarehouseEventType.PUT_DOWN, events[0].type)
        assertEquals("Item-001", events[0].itemLabel)
        assertEquals("Loc-A1", events[0].locationLabel)
    }

    @Test
    fun flicker_does_not_emit_extra_events() {
        val t = tracker()
        t.onFrame(setOf(0, 3), 0L)                             // POBRANO
        assertTrue(t.onFrame(emptySet(), 800L).isEmpty())      // chwilowa utrata < 1500ms
        assertTrue(t.onFrame(setOf(3), 900L).isEmpty())        // znów widoczny, brak zdarzenia
        assertTrue(t.onFrame(setOf(3), 1500L).isEmpty())       // wciąż obecny
    }

    @Test
    fun location_change_updates_zone_of_next_event() {
        val t = tracker()
        t.onFrame(setOf(0, 3), 0L)                             // POBRANO @ Loc-A1
        t.onFrame(setOf(1, 3), 100L)                           // strefa zmienia się na Loc-B2
        val events = t.onFrame(emptySet(), 2000L)              // ZŁOŻONO @ Loc-B2
        assertEquals(1, events.size)
        assertEquals(WarehouseEventType.PUT_DOWN, events[0].type)
        assertEquals("Loc-B2", events[0].locationLabel)
    }

    @Test
    fun item_seen_before_any_location_uses_placeholder() {
        val t = tracker()
        val events = t.onFrame(setOf(3), 0L)                   // brak markera lokalizacji
        assertEquals(1, events.size)
        assertEquals("Loc-?", events[0].locationLabel)
    }

    @Test
    fun snapshot_reports_location_and_present_items() {
        val t = tracker()
        t.onFrame(setOf(1, 4), 0L)                             // Loc-B2 + Item-002
        val state = t.snapshot(0L)
        assertEquals("Loc-B2", state.currentLocation)
        assertTrue(state.presentItems.contains("Item-002"))
    }

    @Test
    fun full_poc_scenario_records_ordered_events() {
        val t = tracker()
        t.onFrame(setOf(0), 0L)                                // wózek w A1
        t.onFrame(setOf(0, 3), 100L)                           // POBRANO Item-001 @ A1
        t.onFrame(setOf(1, 3), 5000L)                          // przejazd do B2 (towar wciąż obecny -> aktualizujemy lastSeen)
        t.onFrame(setOf(1), 5100L)                             // towar zdjęty z wózka
        t.onFrame(emptySet(), 7000L)                           // ZŁOŻONO Item-001 @ B2
        t.onFrame(setOf(2, 4), 8000L)                          // POBRANO Item-002 @ C3

        val log = t.snapshot(8000L).recentEvents               // najnowsze pierwsze
        assertEquals(3, log.size)
        assertEquals(WarehouseEventType.PICKED_UP, log[0].type)
        assertEquals("Item-002", log[0].itemLabel)
        assertEquals("Loc-C3", log[0].locationLabel)
        assertEquals(WarehouseEventType.PUT_DOWN, log[1].type)
        assertEquals("Loc-B2", log[1].locationLabel)
        assertEquals(WarehouseEventType.PICKED_UP, log[2].type)
        assertEquals("Loc-A1", log[2].locationLabel)
    }
}
```

- [ ] **Step 2: Uruchom testy — mają się NIE skompilować**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.mapt.demo.warehouse.WarehouseTrackerTest"`
Expected: FAIL — `WarehouseTracker` nie istnieje.

- [ ] **Step 3: Zaimplementuj tracker**

Utwórz `app/src/main/java/com/mapt/demo/warehouse/WarehouseTracker.kt`:

```kotlin
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
        // 1) Aktualizuj bieżącą lokalizację z dowolnego widocznego markera lokalizacji.
        detectedIds.forEach { id ->
            val marker = markers[id] ?: return@forEach
            if (marker.role == MarkerRole.LOCATION) {
                currentLocationLabel = marker.label
            }
        }

        // 2) Odśwież czas ostatniego widzenia towarów.
        detectedIds.forEach { id ->
            if (markers[id]?.role == MarkerRole.ITEM) {
                itemLastSeenMs[id] = nowMs
            }
        }

        // 3) Oceń obecność każdego znanego towaru z histerezą i emituj zdarzenia.
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
```

- [ ] **Step 4: Uruchom testy — mają przejść**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.mapt.demo.warehouse.WarehouseTrackerTest"`
Expected: PASS (7 testów).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mapt/demo/warehouse/WarehouseTracker.kt app/src/test/java/com/mapt/demo/warehouse/WarehouseTrackerTest.kt
git commit -m "feat(app): WarehouseTracker - maszyna stanow rotacji towaru"
```

---

## Task 6: Android — detekcja wielu markerów w ArucoEngine

Dodajemy metodę zwracającą wszystkie ID. Brak testu jednostkowego (wymaga natywnego OpenCV) — weryfikacja przez kompilację i test ręczny w Task 11. `processFrame` pozostaje bez zmian.

**Files:**
- Modify: `app/src/main/java/com/mapt/demo/ArucoEngine.kt`

- [ ] **Step 1: Dodaj metodę `detectMarkerIds`**

W `ArucoEngine.kt` dodaj nową metodę publiczną tuż po `processFrame` (przed `detectWithFallback`):

```kotlin
    /**
     * Wykrywa wszystkie markery w klatce, rysuje ich ramki i etykiety ID,
     * zwraca zbiór wykrytych ID. Używane przez tryb magazynowy.
     */
    fun detectMarkerIds(rgba: Mat): Set<Int> {
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val result = detectWithFallback(gray)
        gray.release()

        if (result == null) {
            drawStatus(rgba, "Szukam markerow...")
            return emptySet()
        }

        val ids = mutableSetOf<Int>()
        for (i in 0 until result.ids.rows()) {
            ids.add(result.ids.get(i, 0)[0].toInt())
        }

        drawDetectedCorners(rgba, result.corners)
        for (i in result.corners.indices) {
            val points = extractCorners(result.corners[i]) ?: continue
            val id = result.ids.get(i, 0)[0].toInt()
            Imgproc.putText(
                rgba,
                "ID=$id",
                points[0],
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.8,
                Scalar(0.0, 255.0, 0.0, 255.0),
                2
            )
        }

        result.ids.release()
        result.corners.forEach { it.release() }
        return ids
    }
```

- [ ] **Step 2: Zweryfikuj kompilację**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mapt/demo/ArucoEngine.kt
git commit -m "feat(app): ArucoEngine.detectMarkerIds - detekcja wielu markerow"
```

---

## Task 7: Android — warstwa sieciowa zdarzeń magazynowych

**Files:**
- Create: `app/src/main/java/com/mapt/demo/network/WarehouseEventModels.kt`
- Create: `app/src/main/java/com/mapt/demo/network/WarehouseApi.kt`
- Create: `app/src/main/java/com/mapt/demo/network/WarehouseEventRepository.kt`
- Modify: `app/src/main/java/com/mapt/demo/network/RetrofitProvider.kt`

- [ ] **Step 1: Utwórz modele sieciowe**

`app/src/main/java/com/mapt/demo/network/WarehouseEventModels.kt`:

```kotlin
package com.mapt.demo.network

data class WarehouseEventRequest(
    val itemLabel: String,
    val locationLabel: String,
    val type: String,
    val capturedAtEpochMs: Long
)

data class WarehouseEventResponse(
    val accepted: Boolean? = null,
    val message: String? = null,
    val totalEvents: Int? = null
)
```

- [ ] **Step 2: Utwórz interfejs API**

`app/src/main/java/com/mapt/demo/network/WarehouseApi.kt`:

```kotlin
package com.mapt.demo.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface WarehouseApi {
    @POST("api/v1/warehouse/event")
    fun recordEvent(@Body request: WarehouseEventRequest): Call<WarehouseEventResponse>
}
```

- [ ] **Step 3: Utwórz repozytorium**

`app/src/main/java/com/mapt/demo/network/WarehouseEventRepository.kt`:

```kotlin
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
```

- [ ] **Step 4: Dodaj `warehouseApi` do RetrofitProvider**

W `app/src/main/java/com/mapt/demo/network/RetrofitProvider.kt` dodaj wewnątrz `object RetrofitProvider` (po istniejącym `arucoFeedbackApi`):

```kotlin
    val warehouseApi: WarehouseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.ARUCO_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WarehouseApi::class.java)
    }
```

- [ ] **Step 5: Zweryfikuj kompilację**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mapt/demo/network/WarehouseEventModels.kt app/src/main/java/com/mapt/demo/network/WarehouseApi.kt app/src/main/java/com/mapt/demo/network/WarehouseEventRepository.kt app/src/main/java/com/mapt/demo/network/RetrofitProvider.kt
git commit -m "feat(app): warstwa sieciowa zdarzen magazynowych"
```

---

## Task 8: Android — współdzielony podgląd kamery CameraPreview

Wyodrębniamy generyczny podgląd kamery z callbackiem na klatkę `Mat`, by tryb magazynowy nie duplikował obsługi cyklu życia kamery. Tryb nawigacji zostaje na własnym `ArucoCameraView` (bez ryzyka regresji).

**Files:**
- Create: `app/src/main/java/com/mapt/demo/ui/CameraPreview.kt`

- [ ] **Step 1: Utwórz CameraPreview**

`app/src/main/java/com/mapt/demo/ui/CameraPreview.kt`:

```kotlin
package com.mapt.demo.ui

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.core.Mat

/**
 * Generyczny podgląd tylnej kamery OpenCV. Dla każdej klatki wywołuje [onFrame]
 * z obrazem RGBA (na wątku kamery). Rysowanie na [Mat] jest pokazywane w podglądzie.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrame: (Mat) -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val latestOnFrame = rememberUpdatedState(onFrame)
    var cameraViewRef by remember { mutableStateOf<JavaCameraView?>(null) }

    val listener = remember {
        object : CameraBridgeViewBase.CvCameraViewListener2 {
            override fun onCameraViewStarted(width: Int, height: Int) = Unit
            override fun onCameraViewStopped() = Unit
            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat =
                inputFrame.rgba().also { frame -> latestOnFrame.value(frame) }
        }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            JavaCameraView(ctx, CameraBridgeViewBase.CAMERA_ID_BACK).apply {
                visibility = SurfaceView.VISIBLE
                setCameraPermissionGranted()
                setCvCameraViewListener(listener)
                cameraViewRef = this
                enableView()
            }
        }
    )

    DisposableEffect(lifecycleOwner, cameraViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val view = cameraViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.enableView()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> view.disableView()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraViewRef?.disableView()
        }
    }
}
```

- [ ] **Step 2: Zweryfikuj kompilację**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mapt/demo/ui/CameraPreview.kt
git commit -m "feat(app): wspoldzielony CameraPreview"
```

---

## Task 9: Android — ekran trybu magazynowego WarehouseScreen

**Files:**
- Create: `app/src/main/java/com/mapt/demo/warehouse/WarehouseScreen.kt`

- [ ] **Step 1: Utwórz WarehouseScreen**

`app/src/main/java/com/mapt/demo/warehouse/WarehouseScreen.kt`:

```kotlin
package com.mapt.demo.warehouse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapt.demo.ArucoEngine
import com.mapt.demo.ui.CameraPreview

@Composable
fun WarehouseScreen(
    modifier: Modifier = Modifier,
    openCvReady: Boolean,
    apiStatusText: String,
    onEvent: (WarehouseEvent) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) cameraLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    val arucoEngine = remember(openCvReady) {
        if (openCvReady) ArucoEngine(com.mapt.demo.MarkerMapRepository.markerMap) else null
    }
    val tracker = remember { WarehouseTracker() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var uiState by remember { mutableStateOf(WarehouseUiState()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Magazyn — rotacja towaru", style = MaterialTheme.typography.titleMedium)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                !openCvReady -> CenterText("OpenCV nie zostal zainicjalizowany")
                !hasCameraPermission -> CenterText("Brak uprawnienia do kamery")
                arucoEngine == null -> CenterText("Silnik ArUco niedostepny")
                else -> CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrame = { frame ->
                        val now = System.currentTimeMillis()
                        val ids = runCatching { arucoEngine.detectMarkerIds(frame) }.getOrDefault(emptySet())
                        val events = tracker.onFrame(ids, now)
                        val snapshot = tracker.snapshot(now)
                        mainHandler.post {
                            uiState = snapshot
                            events.forEach(onEvent)
                        }
                    }
                )
            }
        }

        StatusPanel(state = uiState, apiStatusText = apiStatusText)

        EventLogPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            events = uiState.recentEvents
        )
    }
}

@Composable
private fun StatusPanel(state: WarehouseUiState, apiStatusText: String) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Bieżąca strefa", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.currentLocation ?: "—", style = MaterialTheme.typography.titleSmall)
            }
            Column {
                Text("Towar na wózku", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (state.presentItems.isEmpty()) "—" else state.presentItems.joinToString(", "),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(state.status, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(apiStatusText, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EventLogPanel(modifier: Modifier = Modifier, events: List<WarehouseEvent>) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Panel(modifier = modifier) {
        Text("Log zdarzeń", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        if (events.isEmpty()) {
            Text("Brak zdarzeń — przesuń towar przed kamerą.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(events) { event ->
                    val typ = if (event.type == WarehouseEventType.PICKED_UP) "POBRANO" else "ZŁOŻONO"
                    Text(
                        "${timeFmt.format(Date(event.epochMs))}  $typ  ${event.itemLabel} @ ${event.locationLabel}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Column(modifier = Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp))
    }
}
```

- [ ] **Step 2: Zweryfikuj kompilację**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mapt/demo/warehouse/WarehouseScreen.kt
git commit -m "feat(app): ekran trybu magazynowego z logiem zdarzen"
```

---

## Task 10: Android — przełącznik trybu w MainActivity

**Files:**
- Modify: `app/src/main/java/com/mapt/demo/MainActivity.kt`

- [ ] **Step 1: Dodaj stan trybu, repozytorium i obsługę zdarzeń**

W `MainActivity` (klasa) dodaj pola obok istniejących (po `arucoFeedbackRepository`):

```kotlin
    private var appMode by mutableStateOf(AppMode.NAVIGATION)
    private var warehouseApiStatus by mutableStateOf("API: oczekiwanie na zdarzenia magazynowe...")
    private val warehouseEventRepository = com.mapt.demo.network.WarehouseEventRepository()
```

- [ ] **Step 2: Dodaj metodę wysyłki zdarzenia**

W `MainActivity` dodaj metodę (obok `sendGeometryFeedbackIfNeeded`):

```kotlin
    private fun sendWarehouseEvent(event: com.mapt.demo.warehouse.WarehouseEvent) {
        warehouseApiStatus = "API: wysyłanie zdarzenia ${event.itemLabel}..."
        warehouseEventRepository.sendEvent(
            itemLabel = event.itemLabel,
            locationLabel = event.locationLabel,
            type = event.type.name,
            capturedAtEpochMs = event.epochMs,
            onSuccess = { response ->
                val msg = response.message?.takeIf { it.isNotBlank() } ?: "zapisano"
                val total = response.totalEvents?.let { " | razem=$it" } ?: ""
                warehouseApiStatus = "API: $msg$total"
            },
            onError = { error -> warehouseApiStatus = "API błąd: $error" }
        )
    }
```

- [ ] **Step 2b: Dodaj enum trybu**

Na końcu pliku `MainActivity.kt` (poza klasą, poza funkcjami composable) dodaj:

```kotlin
enum class AppMode { NAVIGATION, WAREHOUSE }
```

- [ ] **Step 3: Podmień zawartość `setContent` na przełącznik trybu**

Zamień blok `Scaffold { innerPadding -> ArucoScreen(...) }` w `onCreate` na:

```kotlin
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        ModeToggle(
                            mode = appMode,
                            onSelect = { appMode = it }
                        )
                        when (appMode) {
                            AppMode.NAVIGATION -> ArucoScreen(
                                openCvReady = openCvReady,
                                poseUiState = poseState,
                                apiFeedbackText = geometryFeedbackMessage,
                                onPoseDetected = { pose ->
                                    poseState = pose
                                    sendGeometryFeedbackIfNeeded(pose)
                                }
                            )
                            AppMode.WAREHOUSE -> com.mapt.demo.warehouse.WarehouseScreen(
                                openCvReady = openCvReady,
                                apiStatusText = warehouseApiStatus,
                                onEvent = { event -> sendWarehouseEvent(event) }
                            )
                        }
                    }
                }
```

Uwaga: usuń `modifier = Modifier.padding(innerPadding)` z wywołania `ArucoScreen` (padding jest teraz na `Column`). Pozostałe parametry `ArucoScreen` bez zmian.

- [ ] **Step 4: Dodaj composable ModeToggle**

Dodaj nowy composable (np. pod `ArucoScreen`):

```kotlin
@Composable
private fun ModeToggle(mode: AppMode, onSelect: (AppMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.FilterChip(
            selected = mode == AppMode.NAVIGATION,
            onClick = { onSelect(AppMode.NAVIGATION) },
            label = { Text("Nawigacja") }
        )
        androidx.compose.material3.FilterChip(
            selected = mode == AppMode.WAREHOUSE,
            onClick = { onSelect(AppMode.WAREHOUSE) },
            label = { Text("Magazyn") }
        )
    }
}
```

- [ ] **Step 5: Zweryfikuj kompilację**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Jeśli `ArucoScreen` zgłasza nieużywany parametr `modifier`, pozostaw domyślny (`modifier: Modifier = Modifier`) — sygnatura już go ma.

- [ ] **Step 6: Pełny build aplikacji**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/mapt/demo/MainActivity.kt
git commit -m "feat(app): przelacznik trybu Nawigacja/Magazyn + wysylka zdarzen"
```

---

## Task 11: Weryfikacja końcowa i dokumentacja

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Uruchom wszystkie testy aplikacji i backendu**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS (rejestr + tracker + istniejące testy).

Run: `cd backend; .\gradlew.bat :app:test`
Expected: PASS (serwis CSV + kontroler).

- [ ] **Step 2: Test ręczny na urządzeniu (scenariusz PoC „Krok 4”)**

1. Uruchom backend: `.\start-backend.bat` (lub `cd backend; .\gradlew.bat :app:bootRun`).
2. Zainstaluj aplikację: `.\gradlew.bat :app:installDebug`.
3. W aplikacji wybierz zakładkę **Magazyn**.
4. Pokaż kamerze marker ID 0 (Loc-A1) → strefa „Loc-A1”.
5. Pokaż marker ID 3 (Item-001) → w logu pojawia się `POBRANO Item-001 @ Loc-A1`.
6. Zasłoń/zabierz marker ID 3 na >1,5 s → `ZŁOŻONO Item-001 @ Loc-A1`.
7. Pokaż ID 1 (Loc-B2), potem ID 3 → `POBRANO Item-001 @ Loc-B2`.
8. Sprawdź plik `backend/events.csv` (lub katalog roboczy backendu) — zawiera wiersze ze znacznikami czasu.

Oczekiwane: log na ekranie i CSV zgodne ze scenariuszem PoC.

- [ ] **Step 3: Zaktualizuj README**

Dodaj do `README.md` sekcję:

```markdown
## Tryb magazynowy (PoC rotacji towaru)

Aplikacja ma dwa tryby przełączane u góry ekranu:
- **Nawigacja** — estymacja pozycji kamery względem markerów ArUco + mapa GPS.
- **Magazyn** — śledzenie rotacji towaru zgodnie z PoC.

### Markery ArUco (wydrukuj 5 sztuk)
| ID | Rola | Etykieta |
|----|------|----------|
| 0  | Lokalizacja | Loc-A1 |
| 1  | Lokalizacja | Loc-B2 |
| 2  | Lokalizacja | Loc-C3 |
| 3  | Towar | Item-001 |
| 4  | Towar | Item-002 |

Lokalizacje (0–2) powieś na ścianach, towary (3–4) naklej na kartony.

### Jak działa
Kamera telefonu w jednej klatce widzi marker lokalizacji i marker towaru:
- pojawienie się towaru w kadrze → zdarzenie **POBRANO** w bieżącej strefie,
- zniknięcie towaru (po ~1,5 s) → zdarzenie **ZŁOŻONO** w bieżącej strefie.

Zdarzenia trafiają na żywo do logu na ekranie i są wysyłane do backendu, który
dopisuje je do pliku `events.csv` (kolumny: `czas;iso;typ;towar;lokalizacja`).

### Test (scenariusz „Krok 4” z PoC)
Uruchom backend, otwórz tryb Magazyn, przewoź kartony z towarami między strefami
przez 10–15 min. Po teście otwórz `events.csv` i zweryfikuj zarejestrowane
zdarzenia pobrań/złożeń ze znacznikami czasu.
```

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: instrukcja trybu magazynowego (PoC)"
```

---

## Uwagi wykonawcze

- **Wątki:** `onFrame` w `CameraPreview` działa na wątku kamery; `WarehouseScreen` przenosi aktualizację stanu i `onEvent` na główny wątek przez `Handler(Looper.getMainLooper())`. Nie wołaj Compose state setterów spoza głównego wątku.
- **Zasób kamery:** przełączenie trybu usuwa jeden `CameraPreview`/`ArucoCameraView` (dispose → `disableView`) i tworzy drugi, więc tylko jeden trzyma kamerę naraz.
- **Adres backendu:** `BuildConfig.ARUCO_API_BASE_URL` (obecnie `http://10.222.81.5:8080/`). Tryb magazynowy używa tego samego adresu — zaktualizuj go, jeśli backend działa pod innym IP.
- **Ścieżka CSV:** domyślnie `events.csv` w katalogu roboczym backendu; można zmienić przez `warehouse.csv.path` w `application.properties`.
