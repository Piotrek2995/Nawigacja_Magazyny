# Aplikacja do nawigacji w magazynach (ArUco Scanner)

Nawigacja_Magazyny to aplikacja mobilna wspomagająca logistyczne procesy w magazynach wykorzystująca wizję komputerową (OpenCV) i algorytmy nawigacyjne do precyzyjnego określania pozycji użytkownika na podstawie markerów ArUco.

## 🚀 Jak to działa?

1.  **Detekcja**: Kamera skanuje otoczenie w poszukiwaniu markerów ArUco (standard DICT_4X4_50).
2.  **Obliczenia (SolvePnP)**: Aplikacja oblicza dystans i kąt od kamery do markera, a następnie wyznacza pozycję "ego" (Twoją) w przestrzeni.
3.  **Mapa**: System dopasowuje ID markera do bazy danych (np. Marker 23 = Sektor A3, X=10.5, Y=3.2) i wyświetla Twoją aktualną pozycję na mapie magazynu.

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

## 📦 Zależności techniczne

Projekt wykorzystuje:
*   **OpenCV (4.5.3)**: Do przetwarzania obrazu i detekcji markerów.
*   **CameraX**: Do wydajnej obsługi podglądu z kamery w Jetpack Compose.
*   **Jetpack Compose**: Nowoczesny interfejs użytkownika.
*   **SolvePnP**: Algorytm do estymacji pozycji 3D na podstawie punktów 2D.

## ⚠️ Uwagi
Dla uzyskania najlepszej dokładności:
*   Marker powinien być płaski (nie pognieciony).
*   Oświetlenie powinno być równomierne.
*   Parametry kamery w funkcji `estimateRelativePosition` są przybliżone. Dla profesjonalnych zastosowań zaleca się przeprowadzenie kalibracji kamery konkretnego urządzenia.
*  Witam