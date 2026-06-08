# Tryb magazynowy (Warehouse mode) — projekt

Data: 2026-06-08
Status: zaakceptowany do implementacji

## 1. Cel i kontekst

Aplikacja demonstracyjna ma zrealizować PoC z dokumentu *„Optymalizacja rozmieszczenia
asortymentu”*: śledzenie rotacji towaru w magazynie przy pomocy kamery i markerów.
Zamiast dwóch kamer (telefon + laptop) i kodów QR opisanych w PoC, wykorzystujemy
**jedną kamerę telefonu** oraz **istniejące markery ArUco** — detekcja ArUco jest już
zaimplementowana i solidna.

Tryb magazynowy jest **dodawany obok** istniejącego trybu nawigacji ArUco (estymacja
pozycji + mapa GPS). Istniejący tryb pozostaje nietknięty.

## 2. Mapowanie pojęć PoC → aplikacja

| PoC | Realizacja w aplikacji |
|-----|------------------------|
| Kod QR lokalizacji na ścianie (Loc-A1) | Marker ArUco o roli LOKALIZACJA |
| Kod QR towaru na kartonie (Item-001) | Marker ArUco o roli TOWAR |
| Kamera 1 (telefon) widzi lokalizację | Ta sama klatka kamery telefonu |
| Kamera 2 (laptop) widzi towar na wózku | Ta sama klatka kamery telefonu |
| „Wózek jest w strefie A1” | Bieżąca lokalizacja = ostatnio widziany marker lokalizacji |
| Pojawienie się towaru na wózku → Pobrano | Marker towaru pojawia się w kadrze → zdarzenie POBRANO |
| Zniknięcie towaru z wózka → Odłożono | Marker towaru znika z kadru → zdarzenie ZŁOŻONO |
| Zapis do pliku CSV | Backend dopisuje wiersz do `events.csv` |

### Rejestr markerów (wydrukowane ID 0–4)

| ID ArUco | Rola | Etykieta |
|----------|------|----------|
| 0 | LOKALIZACJA | Loc-A1 |
| 1 | LOKALIZACJA | Loc-B2 |
| 2 | LOKALIZACJA | Loc-C3 |
| 3 | TOWAR | Item-001 |
| 4 | TOWAR | Item-002 |

Słownik ArUco nie jest twardo ustalony — `ArucoEngine` wykrywa wiele słowników z
fallbackiem, więc działa niezależnie od tego, którym słownikiem wygenerowano druk.

## 3. Logika śledzenia (maszyna stanów)

Dla każdej klatki kamera zwraca **zbiór ID** wykrytych markerów. `WarehouseTracker`
(czysta logika, bez zależności od Androida) przetwarza ten zbiór wraz z bieżącym
czasem i emituje listę zdarzeń.

Reguły:

1. **Bieżąca lokalizacja** — jeśli w klatce jest marker lokalizacji, ustaw bieżącą
   lokalizację na jego etykietę. Bieżąca lokalizacja jest „zapamiętywana”, dopóki nie
   pojawi się inny marker lokalizacji (towar można pobrać/złożyć nawet gdy w danym
   momencie żaden marker lokalizacji nie jest widoczny — używamy ostatnio znanej).
2. **Obecność towaru (debounce)** — towar jest uznawany za OBECNY, gdy był widziany w
   ciągu ostatnich `PRESENCE_TTL_MS` (np. 800 ms). Towar jest uznawany za NIEOBECNY,
   gdy nie był widziany przez `ABSENCE_TTL_MS` (np. 1500 ms). Histereza eliminuje
   migotanie detekcji.
3. **Zdarzenia:**
   - przejście NIEOBECNY → OBECNY = **POBRANO** (towar trafił na wózek z bieżącej lokalizacji),
   - przejście OBECNY → NIEOBECNY = **ZŁOŻONO** (towar zdjęty z wózka w bieżącej lokalizacji).
4. **Brak znanej lokalizacji** — jeśli nigdy nie zobaczono markera lokalizacji,
   etykieta lokalizacji w zdarzeniu = `Loc-?`.

Parametry progów są stałymi w jednym miejscu (łatwa korekta podczas testów na żywo).

## 4. Komponenty

### Android (`app/src/main/java/com/mapt/demo/`)

- **`warehouse/WarehouseModels.kt`**
  - `MarkerRole { LOCATION, ITEM }`
  - `WarehouseMarker(id: Int, role: MarkerRole, label: String)`
  - `WarehouseRegistry` — obiekt z mapą `Int -> WarehouseMarker` (ID 0–4 jak wyżej),
    pomocnicze `roleOf(id)`, `labelOf(id)`.
  - `WarehouseEventType { PICKED_UP, PUT_DOWN }`
  - `WarehouseEvent(itemLabel, locationLabel, type, epochMs)`
  - `WarehouseUiState(currentLocation: String?, presentItems: List<String>,
    recentEvents: List<WarehouseEvent>, status: String, apiStatus: String)`

- **`warehouse/WarehouseTracker.kt`** — czysta maszyna stanów.
  - `fun onFrame(detectedIds: Set<Int>, nowMs: Long): List<WarehouseEvent>`
  - `fun snapshot(nowMs: Long): WarehouseUiState` (bieżąca lokalizacja, obecne towary)
  - Stan wewnętrzny: `currentLocationLabel`, mapa `itemId -> lastSeenMs`, mapa
    `itemId -> Boolean present`. Brak zależności Androidowych → test JVM.

- **`ArucoEngine.kt`** — dodanie metody:
  - `fun detectMarkerIds(rgba: Mat): Set<Int>` — reużywa `detectWithFallback`,
    zwraca wszystkie wykryte ID, rysuje na klatce ramki + etykiety (kolor zależny od
    roli: lokalizacja vs towar). `processFrame` (estymacja pozycji) pozostaje bez zmian.
  - Refaktor: `detectWithFallback` jest rozszerzony tak, by zwracać **wszystkie** ID z
    pierwszego pasującego słownika (obecnie zwraca tylko pierwszy marker). Zmiana nie
    psuje `processFrame` — bierze on dalej `corners.first()`/`ids.get(0,0)`.

- **`MainActivity.kt`** — przełącznik trybu na górze (`Nawigacja` / `Magazyn`):
  - `Nawigacja` → istniejący `ArucoScreen` (bez zmian).
  - `Magazyn` → nowy `WarehouseScreen`.
  - Stan trybu trzymany w Activity; logika magazynu (tracker + wysyłka zdarzeń) w
    osobnej sekcji analogicznej do `sendGeometryFeedbackIfNeeded`.

- **`warehouse/WarehouseScreen.kt`** (Compose):
  - Podgląd kamery (reużyty `ArucoCameraView`, ale z callbackiem na `Set<Int>` zamiast
    `PoseUiState` — patrz niżej) z narysowanymi markerami.
  - Karta „Bieżąca lokalizacja” + „Towary na wózku”.
  - **Lista zdarzeń na żywo** (zamiast mapy MapLibre) — najnowsze u góry,
    format `hh:mm:ss POBRANO/ZŁOŻONO Item-XXX @ Loc-YY`.
  - Status backendu (potwierdzenia/błędy zapisu CSV).
  - MapLibre **nie** występuje w tym trybie.

- **Reużycie podglądu kamery** — `ArucoCameraView` zostaje uogólniony lub powstaje
  bliźniaczy `MarkerCameraView`, który zamiast `onPoseDetected: (PoseUiState)` przyjmuje
  `onFrameProcessed: () -> Unit` z przekazaniem klatki do wybranego silnika. Aby nie
  komplikować: dodajemy parametr `frameProcessor: (Mat) -> Unit` i wywołujemy go w
  `onCameraFrame`; tryb nawigacji przekazuje processor wołający `processFrame`, tryb
  magazynu — processor wołający `detectMarkerIds` + `tracker.onFrame`.

- **Sieć (`network/`)**
  - `WarehouseApi` — `@POST("api/v1/warehouse/event")`.
  - `WarehouseEventModels` — `WarehouseEventRequest(itemLabel, locationLabel, type,
    capturedAtEpochMs)`, `WarehouseEventResponse(accepted, message, totalEvents)`.
  - `WarehouseEventRepository` — wysyłka zdarzenia (Retrofit, enqueue), callbacki
    onSuccess/onError, analogicznie do `ArucoFeedbackRepository`.
  - `RetrofitProvider` — dodanie `warehouseApi` (ten sam baseUrl).

### Backend (`backend/app/src/main/kotlin/com/mapt/demo/backend/`)

- **`WarehouseController.kt`**
  - `POST /api/v1/warehouse/event` — przyjmuje `WarehouseEventRequest`, deleguje do
    serwisu CSV, zwraca `WarehouseEventResponse(accepted=true, message, totalEvents)`.
  - `GET /api/v1/warehouse/log` — zwraca listę ostatnich zdarzeń (do podglądu/diagnostyki).
- **`WarehouseCsvService.kt`**
  - Thread-safe (synchronized) dopisywanie wiersza do `events.csv`.
  - Format wiersza: `czas_iso;typ;towar;lokalizacja` (separator `;`, nagłówek tworzony
    przy pierwszym zapisie). Czas zapisywany czytelnie (`HH:mm:ss`) + pełny ISO w
    osobnej kolumnie dla jednoznaczności.
  - Ścieżka pliku konfigurowalna (`application.properties`, domyślnie `events.csv` w
    katalogu roboczym).
  - Licznik zdarzeń zwracany w odpowiedzi.

## 5. Przepływ danych

```
Klatka kamery (Mat)
  → ArucoEngine.detectMarkerIds → Set<Int>
  → WarehouseTracker.onFrame(ids, now) → List<WarehouseEvent>
       ├─ aktualizacja WarehouseUiState (lokalizacja, towary, log) → UI
       └─ dla każdego zdarzenia: WarehouseEventRepository.send → backend
              → WarehouseCsvService.append → events.csv
              → WarehouseEventResponse → aktualizacja statusu API w UI
```

Przykład zawartości `events.csv` po teście:
```
czas;iso;typ;towar;lokalizacja
14:35:05;2026-06-08T14:35:05;POBRANO;Item-001;Loc-A1
14:36:20;2026-06-08T14:36:20;ZŁOŻONO;Item-001;Loc-B2
14:37:10;2026-06-08T14:37:10;POBRANO;Item-002;Loc-C3
```

## 6. Obsługa błędów

- Detekcja klatki rzuca wyjątek → status „Błąd przetwarzania klatki”, tracker pomija klatkę.
- Brak połączenia z backendem → zdarzenie i tak ląduje w logu na ekranie; status API
  pokazuje błąd; zdarzenie nie jest tracone z widoku (CSV po stronie serwera może go
  nie zawierać — to akceptowalne dla PoC, log ekranowy jest źródłem prawdy w UI).
- Wykryty marker spoza rejestru (ID > 4) → ignorowany (ani lokalizacja, ani towar).

## 7. Testy

- **`WarehouseTrackerTest`** (JVM, TDD — pisany przed implementacją trackera):
  - pojawienie się towaru → dokładnie jedno zdarzenie POBRANO z bieżącą lokalizacją,
  - zniknięcie po przekroczeniu `ABSENCE_TTL_MS` → jedno zdarzenie ZŁOŻONO,
  - migotanie (towar znika na < `ABSENCE_TTL_MS` i wraca) → brak fałszywych zdarzeń,
  - zmiana markera lokalizacji aktualizuje strefę kolejnych zdarzeń,
  - towar pobrany zanim zobaczono jakąkolwiek lokalizację → etykieta `Loc-?`,
  - sekwencja pełnego scenariusza PoC (pobrano A1 → przejazd → złożono B2).
- **Backend `WarehouseControllerTest`**: POST zdarzenia zapisuje poprawny wiersz CSV i
  zwraca `totalEvents`; GET zwraca dopisane zdarzenia.

## 8. Poza zakresem (YAGNI)

- Brak uwierzytelniania, bazy danych (CSV wystarczy do PoC).
- Brak trybu dwukamerowego (świadoma decyzja: jedna klatka = lokalizacja + towar).
- Brak analityki rotacji/raportów — PoC kończy się na poprawnym logu zdarzeń.
- Brak zmian w trybie nawigacji ArUco poza dodaniem metody `detectMarkerIds`.

## 9. README / instrukcja testu

Dokumentacja kroku testowego (analogiczna do „Krok 4” z PoC): wydrukowane markery
0–2 na ścianach (lokalizacje), 3–4 na kartonach (towary); przenoszenie kartonów przed
kamerą telefonu; po teście weryfikacja `events.csv`.
