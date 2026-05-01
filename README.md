# Aplikacja do nawigacji w magazynach (ArUco Scanner)

Nawigacja_Magazyny to aplikacja mobilna wspomagająca logistyczne procesy w magazynach wykorzystująca wizję komputerową (OpenCV) i algorytmy nawigacyjne do precyzyjnego określania pozycji użytkownika na podstawie markerów ArUco.

## 🚀 Jak to działa?

1.  **Detekcja**: Kamera skanuje otoczenie w poszukiwaniu markerów ArUco (standard DICT_4X4_50).
2.  **Obliczenia (SolvePnP)**: Aplikacja oblicza dystans i kąt od kamery do markera, a następnie wyznacza pozycję "ego" (Twoją) w przestrzeni.
3.  **Mapa**: System dopasowuje ID markera do bazy danych (np. Marker 23 = Sektor A3, X=10.5, Y=3.2) i wyświetla Twoją aktualną pozycję na mapie magazynu.
4.  **Feedback**: Aplikacja wysyła dane geometryczne markera do backendu REST API.

---

## 🛠️ Instrukcja konfiguracji krok po kroku

### 1. Przygotowanie markerów
Aplikacja korzysta z biblioteki markerów **4x4 (50)**.
*   Wygeneruj i wydrukuj markery o ID zawartych w kodzie (domyślnie: **23** i **42**).
*   Możesz to zrobić na stronie: [https://chev.me/arucogen/](https://chev.me/arucogen/) (wybierz Dictionary: 4x4).
*   **Ważne**: W kodzie przyjęto rozmiar boku markera `0.1m` (10 cm). Jeśli drukujesz inny rozmiar, zmień wartość `markerSize` w klasie `ArUcoAnalyzer`.

### 2. Konfiguracja mapy markerów
W pliku `MainActivity.kt` znajdziesz zmienną `markerMap`. Edytuj ją, aby odpowiadała rzeczywistemu rozmieszczeniu markerów w Twoim magazynie:

```kotlin
val markerMap = mapOf(
    23 to MarkerInfo("Sektor A3", 10.5, 3.2), // ID 23 znajduje się na współrzędnych X=10.5, Y=3.2
    42 to MarkerInfo("Sektor B1", 2.0, 7.8)
)
```

### 3. Instalacja i uprawnienia
*   Sklonuj projekt i otwórz go w **Android Studio**.
*   Zsynchronizuj projekt z plikami Gradle.
*   Uruchom aplikację na fizycznym urządzeniu z systemem Android (emulatory często mają problem z obsługą kamery pod OpenCV).
*   Przy pierwszym uruchomieniu zaakceptuj uprawnienia do **kamery**.

### 4. Skanowanie
*   Skieruj aparat na marker ArUco.
*   Na dole ekranu pojawi się komunikat z rozpoznanym sektorem oraz Twoimi przybliżonymi współrzędnymi `X` i `Y`.

---

## 🌐 Backend API i Komunikacja

### Uruchamianie Backend'u

Projekt zawiera gotowy backend napisany w **Spring Boot (Kotlin)**, który obsługuje feedback'i z aplikacji mobile.

**Szybki Start:**
```powershell
# Opcja 1: Prosty klik
start-backend.bat

# Opcja 2: PowerShell
.\backend-manager.ps1 start

# Opcja 3: Terminal
cd backend
./gradlew :app:bootRun
```

Backend nasłuchuje na: **http://localhost:8080**

**Endpoint API:**
```
POST /api/v1/aruco/geometry-feedback
```

### Testowanie Backend'u

1. **W Postmanie:**
   - Zaimportuj: `postman/ArucoGeometryFeedback.postman_collection.json`
   - Ustaw `baseUrl = http://localhost:8080`
   - Wyślij request "Send ArUco geometry feedback"

2. **W Przeglądarce (Health Check):**
   ```
   http://localhost:8080/actuator/health
   ```

3. **Z Command Line:**
   ```powershell
   curl -X POST http://localhost:8080/api/v1/aruco/geometry-feedback `
     -H "Content-Type: application/json" `
     -d '{"markerId": 42, "location": "B1", ...}'
   ```

### Zatrzymanie Backend'u

```powershell
# Opcja 1: Prosty klik
stop-backend.bat

# Opcja 2: PowerShell
.\backend-manager.ps1 stop

# Opcja 3: Ctrl+C w terminalu (jeśli uruchomiony)
```

---

## 📄 Dokumentacja

Pełne instrukcje dotyczące zarządzania backendem i integracją:

| Plik | Opis |
|------|------|
| **NARZEDZIA.md** | 🛠️ Jak uruchamiać i zatrzymywać backend |
| **BACKEND_INSTRUKCJA.md** | 📚 Pełna instrukcja backendu (konfiguracja, troubleshooting) |
| **INTEGRACJA_ANDROID.md** | 📱 Jak zintegrować backend z aplikacją Android |
| **postman/ArucoGeometryFeedback.postman_collection.json** | 🧪 Kolekcja testów Postman |

---

## 📦 Zależności techniczne

### Aplikacja Android
Projekt wykorzystuje:
*   **OpenCV (4.5.3)**: Do przetwarzania obrazu i detekcji markerów.
*   **CameraX**: Do wydajnej obsługi podglądu z kamery w Jetpack Compose.
*   **Jetpack Compose**: Nowoczesny interfejs użytkownika.
*   **SolvePnP**: Algorytm do estymacji pozycji 3D na podstawie punktów 2D.
*   **Retrofit 2**: Komunikacja HTTP z API feedbacku geometrii ArUco.

### Backend
*   **Spring Boot 3.2.0**: Framework webowy
*   **Kotlin**: Język programowania
*   **Gradle**: Build system
*   **Java 21**: Runtime

---

## 🔗 Konfiguracja Komunikacji Android ↔ Backend

### W Emulatorze Android
```kotlin
// app/build.gradle.kts
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://10.0.2.2:8080/\"")
```

### Na Urządzeniu Fizycznym
```kotlin
// app/build.gradle.kts
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://192.168.1.10:8080/\"")
// Zmień 192.168.1.10 na swoje IP (ipconfig w terminalu)
```

Domyślnie URL bazowy API jest ustawiony w `app/build.gradle.kts` jako:
`ARUCO_API_BASE_URL = "http://10.0.2.2:8080/"`

Jeśli backend działa pod innym adresem, zmień wartość `ARUCO_API_BASE_URL` i przebuduj aplikację.

---

## ⚠️ Uwagi
Dla uzyskania najlepszej dokładności:
*   Marker powinien być płaski (nie pognieciony).
*   Oświetlenie powinno być równomierne.
*   Parametry kamery w funkcji `estimateRelativePosition` są przybliżone. Dla profesjonalnych zastosowań zaleca się przeprowadzenie kalibracji kamery konkretnego urządzenia.
*   Backend musi być uruchomiony, aby aplikacja mogła wysyłać feedback'i.

---

## 🎯 Szybki Start (3 kroki)

1️⃣ **Uruchom Backend**
```powershell
start-backend.bat
```

2️⃣ **Testuj w Postmanie**
- Zaimportuj kolekcję
- Wyślij żądanie

3️⃣ **Zatrzymaj Backend**
```powershell
stop-backend.bat
```

---

**Version:** 2.0 (z Backend API)  
**Last Updated:** 2026-04-29
