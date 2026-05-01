# 🚀 Zarządzanie Backend'em i Komunikacją API

## 📋 Spis treści
1. [Uruchamianie Backend'u](#uruchamianie-backendu)
2. [Zatrzymywanie Backend'u](#zatrzymywanie-backendu)
3. [Testowanie w Postmanie](#testowanie-w-postmanie)
4. [Konfiguracja w Aplikacji Android](#konfiguracja-w-aplikacji-android)
5. [Rozwiązywanie Problemów](#rozwiązywanie-problemów)

---

## 🟢 Uruchamianie Backend'u

### Opcja 1: Użyj skryptu (Najprostrze)
1. W folderze głównym projektu (`Demo/`) kliknij dwukrotnie na `start-backend.bat`
2. Czekaj aż pojawi się komunikat: `Tomcat started on port(s): 8080`
3. Backend jest gotowy do pracy!

### Opcja 2: Ręcznie z terminala
```powershell
cd backend
./gradlew :app:bootRun
```

### Opcja 3: Zbuduj JAR i uruchom (.jar)
```powershell
cd backend
./gradlew :app:bootJar
cd app
java -jar build/libs/demo-backend-*.jar
```

---

## 🔴 Zatrzymywanie Backend'u

### Opcja 1: Skrypt (Najprostrze)
1. W folderze głównym projektu (`Demo/`) kliknij dwukrotnie na `stop-backend.bat`
2. Backend zostanie zatrzymany

### Opcja 2: Ręcznie w Terminalu
- Jeśli backend jest uruchomiony w terminalu: **Ctrl+C**

### Opcja 3: Przez Menedżer Zadań
1. Otwórz Menedżer Zadań (Ctrl+Shift+Esc)
2. Szukaj procesu Java (`java.exe` lub `gradlew`)
3. Prawy klik → Zakończ zadanie

---

## 🧪 Testowanie w Postmanie

### 1. Uruchom Backend
```
./gradlew :app:bootRun
```

### 2. Otwórz Postman
- Zaimportuj kolekcję: `postman/ArucoGeometryFeedback.postman_collection.json`

### 3. Konfiguracja zmiennej
- Upewnij się, że `baseUrl` = `http://localhost:8080`

### 4. Wyślij Request
- Kliknij na "Send ArUco geometry feedback" (POST)
- Kliknij **Send**

### 5. Oczekiwana odpowiedź (200 OK)
```json
{
  "accepted": true,
  "message": "Feedback received for marker 42",
  "qualityScore": 0.95,
  "correctedX": 2.41,
  "correctedY": 6.72
}
```

---

## 🔧 Konfiguracja w Aplikacji Android

### Lokalizacja: `app/build.gradle.kts`

#### Domyślna konfiguracja (dla emulatora):
```kotlin
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://10.0.2.2:8080/\"")
```
- `10.0.2.2` = punkt dostępu do hosta z emulatora Android

#### Zmiana na urządzenie fizyczne:
Jeśli testujesz na urządzeniu fizycznym, zmień na IP Twojego komputera:
```kotlin
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://192.168.1.10:8080/\"")
```
(Zastąp `192.168.1.10` swoim IP)

#### Jak znaleźć swoje IP:
```powershell
ipconfig
```
Szukaj `IPv4 Address` (np. `192.168.x.x`)

### Struktura Retrofit (już skonfigurowana):

**ArucoFeedbackApi.kt** - Interface API:
```kotlin
@RestController
@RequestMapping("/api/v1/aruco")
class ArucoFeedbackApi {
    @POST("api/v1/aruco/geometry-feedback")
    fun sendGeometryFeedback(@Body request: ArucoGeometryFeedbackRequest): Call<ArucoGeometryFeedbackResponse>
}
```

**RetrofitProvider.kt** - Klient HTTP:
```kotlin
object RetrofitProvider {
    val arucoFeedbackApi: ArucoFeedbackApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.ARUCO_API_BASE_URL)  // Używa URL z build.gradle.kts
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArucoFeedbackApi::class.java)
    }
}
```

---

## 🐛 Rozwiązywanie Problemów

### Problem: `Error: connect ECONNREFUSED 127.0.0.1:8080`
**Rozwiązanie:** Backend nie jest uruchomiony.
```powershell
# Uruchom backend:
cd backend
./gradlew :app:bootRun
```

### Problem: Port 8080 już zajęty
**Rozwiązanie:** Zmień port w konfiguracji:
```properties
# backend/app/src/main/resources/application.properties
server.port=9090
```
Pamiętaj zmienić `baseUrl` w Postmanie i `build.gradle.kts` aplikacji Android!

### Problem: Backend działa, ale aplikacja Android się nie łączy
**Rozwiązanie:** Sprawdź IP:
1. Urządzenie w sieci WiFi (emulator z hosta):
   ```kotlin
   "http://10.0.2.2:8080/"  // Domyślne
   ```

2. Urządzenie fizyczne (inny komputer w sieci):
   ```kotlin
   "http://192.168.1.10:8080/"  // Zmień IP na swoje
   ```

3. Test z localhost:
   ```bash
   curl http://localhost:8080/api/v1/aruco/geometry-feedback -X POST \
     -H "Content-Type: application/json" \
     -d '{"markerId": 42, "location": "B1", "worldX": 2.31, "worldY": 6.77, "distanceMeters": 1.28, "cameraXInMarker": -0.24, "cameraZInMarker": 1.14, "capturedAtEpochMs": 1714291200000}'
   ```

### Problem: Chcę zmienić port backendu
**Rozwiązanie:** Edytuj tę linię w pliku `application.properties`:
```properties
server.port=8080  # Zmień na inny port np. 9090
```

---

## 📞 Struktura API

### Endpoint
```
POST /api/v1/aruco/geometry-feedback
Content-Type: application/json
```

### Request Body
```json
{
  "markerId": 42,
  "location": "B1",
  "worldX": 2.31,
  "worldY": 6.77,
  "distanceMeters": 1.28,
  "cameraXInMarker": -0.24,
  "cameraZInMarker": 1.14,
  "capturedAtEpochMs": 1714291200000
}
```

### Response (200 OK)
```json
{
  "accepted": true,
  "message": "Feedback received for marker 42",
  "qualityScore": 0.95,
  "correctedX": 2.41,
  "correctedY": 6.72
}
```

---

## 🎯 Szybki Start

```powershell
# 1. Uruchom backend
start-backend.bat

# 2. Otwórz Postman, zaimportuj ArucoGeometryFeedback.postman_collection.json

# 3. Upewnij się baseUrl = http://localhost:8080

# 4. Wyślij żądanie "Send ArUco geometry feedback"

# 5. Gdy skończysz, zatrzymaj backend
stop-backend.bat
```

---

## 📱 Integracja z Aplikacją Android

Aplikacja Android już ma skonfigurowany Retrofit do komunikacji z tym API.
Kod w `app/src/main/java/com/mapt/demo/network/`:
- `ArucoFeedbackApi.kt` - Interface Retrofit
- `ArucoFeedbackModels.kt` - Modele danych
- `ArucoFeedbackRepository.kt` - Logika dostępu do danych
- `RetrofitProvider.kt` - Inicjalizacja klienta HTTP

Włączenie wysyłania feedback'u w aplikacji należy zrobić w logice przechwytywania markerów (prawdopodobnie w `MainActivity.kt` lub `ArucoEngine.kt`).

---

## ✅ Sprawdzenie Statusu

Aby sprawdzić, czy backend działa:
```powershell
# Postman - GET http://localhost:8080/api/v1/aruco/geometry-feedback
# Lub w terminalu:
curl http://localhost:8080/actuator/health
```

Odpowiedź powinna być:
```json
{
  "status": "UP"
}
```

---

**Version:** 1.0  
**Last Updated:** 2026-04-29

