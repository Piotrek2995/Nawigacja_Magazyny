# 📱 Integracja Backend'u z Aplikacją Android

## 🎯 Jak Wysyłać Feedback do Backend'u

### 1. Istnieje już klasa Repository
Klasa `ArucoFeedbackRepository` (plik `app/src/main/java/com/mapt/demo/network/ArucoFeedbackRepository.kt`) jest gotowa do wysyłania danych:

```kotlin
val repository = ArucoFeedbackRepository()

repository.sendGeometryFeedback(
    markerId = 42,
    location = "B1",
    worldX = 2.31,
    worldY = 6.77,
    distanceMeters = 1.28,
    cameraXInMarker = -0.24,
    cameraZInMarker = 1.14,
    onSuccess = { response ->
        Log.d("TAG", "Feedback accepted: ${response.message}")
        Log.d("TAG", "Quality score: ${response.qualityScore}")
    },
    onError = { error ->
        Log.e("TAG", "Error sending feedback: $error")
    }
)
```

### 2. Integracja w MainActivity (Przykład)

Gdy wykryjesz marker ArUco, możesz wysłać feedback:

```kotlin
// Gdzieś w kodzie MainActivity.kt, gdy zostanie wykryty marker
if (markerDetected) {
    val repository = ArucoFeedbackRepository()
    
    repository.sendGeometryFeedback(
        markerId = detectedMarkerId,
        location = getLocationName(detectedMarkerId),
        worldX = estimatedX,
        worldY = estimatedY,
        distanceMeters = distanceToMarker,
        cameraXInMarker = cameraX,
        cameraZInMarker = cameraZ,
        onSuccess = { response ->
            // Backend zaakceptował feedback
            showMessage("Feedback: ${response.message}")
        },
        onError = { error ->
            // Błąd wysyłania
            showMessage("Błąd: $error")
        }
    )
}
```

### 3. Konfiguracja Backend URL

Adres backendu jest zdefiniowany w pliku `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://10.0.2.2:8080/\"")
```

**Dla emulatora:** `http://10.0.2.2:8080/`  
**Dla urządzenia fizycznego:** `http://TWOJE_IP:8080/` (np. `http://192.168.1.10:8080/`)

Po zmianie musisz przebudować aplikację (Build → Rebuild Project).

---

## 🔄 Przepływ Danych

```
Aplikacja Android
       ↓
  ArucoEngine.kt (detekuje marker)
       ↓
  MainActivity.kt (przetwarza wynik)
       ↓
  ArucoFeedbackRepository.sendGeometryFeedback()
       ↓
  RetrofitProvider.arucoFeedbackApi
       ↓
  HTTP POST → Backend (localhost:8080)
       ↓
  ArucoController.sendGeometryFeedback()
       ↓
  Odpowiedź JSON do aplikacji
```

---

## ✅ Wymagania do Działania

1. **Backend uruchomiony:**
   ```powershell
   cd backend
   ./gradlew :app:bootRun
   ```

2. **Permissions w AndroidManifest.xml** (już powinny być):
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.CAMERA" />
   ```

3. **Aplikacja Android uruchomiona** na emulatorze lub urządzeniu z:
   - Prawidłowym URL backendu
   - Uprawnieniami do internetu i kamery

---

## 🧪 Testowanie

### Test 1: Backend bez aplikacji
```powershell
# Terminal
curl -X POST http://localhost:8080/api/v1/aruco/geometry-feedback \
  -H "Content-Type: application/json" \
  -d '{...json...}'
```

### Test 2: Postman
- Zaimportuj `postman/ArucoGeometryFeedback.postman_collection.json`
- Wyślij request

### Test 3: Aplikacja Android
- Uruchom aplikację
- Pokieruj kamerą na marker ArUco
- Sprawdzaj logcat czy feedback się wysyła

---

## 📝 Szybka Ściąga

| Zadanie | Komenda/Plik |
|---------|------------|
| Uruchomić backend | `start-backend.bat` |
| Zatrzymać backend | `stop-backend.bat` |
| Zmienić port | `backend/app/src/main/resources/application.properties` |
| Zmienić URL w aplikacji | `app/build.gradle.kts` (ARUCO_API_BASE_URL) |
| Wysłać feedback | `ArucoFeedbackRepository.sendGeometryFeedback(...)` |
| Testować w Postmanie | `postman/ArucoGeometryFeedback.postman_collection.json` |

---

## 🐛 Debugowanie

### Logi Backendu (console)
Logi są dla pakietu `com.mapt.demo.backend` na poziomie DEBUG.  
Możesz zmienić w: `backend/app/src/main/resources/application.properties`

### Logi Android
```kotlin
Log.d("ArucoFeedback", "Message: $message")
Log.e("ArucoFeedback", "Error: $error")
```

Zobaczysz w Logcat w Android Studio.

### Testowanie Połączenia
```kotlin
// Wstaw gdzieś w MainActivity.onCreate():
Thread {
    try {
        val result = java.net.URL("http://localhost:8080/actuator/health")
            .openConnection()
            .inputStream
            .bufferedReader()
            .use { it.readText() }
        Log.d("Health", result)
    } catch (e: Exception) {
        Log.e("Health", e.message ?: "Unknown error")
    }
}.start()
```

---

**Version:** 1.0  
**Last Updated:** 2026-04-29

