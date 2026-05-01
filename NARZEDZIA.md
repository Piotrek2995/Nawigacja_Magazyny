# 🛠️ Narzędzia do Zarządzania Backend'em

W projekcie znajdują się łatwe do użycia narzędzia do uruchamiania i zatrzymywania backend'u:

---

## 📦 Dostępne Narzędzia

### 1. **start-backend.bat** (Windows - Prosty klik)
Najłatwiejszy sposób uruchamiania backendu.

**Użycie:**
- Kliknij dwukrotnie na `start-backend.bat` w folderze głównym projektu

---

### 2. **stop-backend.bat** (Windows - Prosty klik)
Zatrzymuje backend działający na porcie 8080.

**Użycie:**
- Kliknij dwukrotnie na `stop-backend.bat` w folderze głównym projektu

---

### 3. **backend-manager.ps1** (PowerShell - Zaawansowany)
Zaawansowany manager do pełnego kontrolowania backendu.

**Wymagania:**
```powershell
# Enable-ExecutionPolicy LocalUser -ExecutionPolicy RemoteSigned
```

**Użycie:**

```powershell
# Sprawdzenie statusu (domyślne)
.\backend-manager.ps1

# Uruchomienie backendu
.\backend-manager.ps1 start

# Zatrzymanie backendu
.\backend-manager.ps1 stop

# Restart backendu
.\backend-manager.ps1 restart

# Wyświetlanie logów
.\backend-manager.ps1 logs

# Pomoc
.\backend-manager.ps1 help
```

---

### 4. **Z Terminala (Ręcznie)**

**Uruchamianie:**
```powershell
cd backend
./gradlew :app:bootRun
```

**Zatrzymanie:**
- Naciśnij `Ctrl+C` w terminalu

---

## 🎯 Szybki Start (3 kroki)

### Krok 1: Uruchom Backend
```powershell
# Opcja A: Klik na start-backend.bat
start-backend.bat

# Opcja B: Z PowerShell
.\backend-manager.ps1 start

# Opcja C: Z Command Line
cd backend && gradlew :app:bootRun
```

### Krok 2: Sprawdź Czy Działa
```powershell
# PowerShell
.\backend-manager.ps1 status

# Lub otwórz w przeglądarce:
# http://localhost:8080/actuator/health
```

### Krok 3: Testuj w Postmanie
1. Zaimportuj `postman/ArucoGeometryFeedback.postman_collection.json`
2. Ustaw `baseUrl = http://localhost:8080`
3. Wyślij request "Send ArUco geometry feedback"

---

## 📊 Porównanie Metod

| Metoda | Łatwość | Zaawansowana | Zaleta |
|--------|---------|--------------|--------|
| start-backend.bat | ⭐⭐⭐ | ❌ | Najprostrze (klik) |
| backend-manager.ps1 | ⭐⭐ | ✅ | Pełna kontrola |
| Terminal | ⭐ | ✅ | Maksymalna kontrola + logi |
| stop-backend.bat | ⭐⭐⭐ | ❌ | Szybkie zatrzymanie |

---

## 🔧 Konfiguracja

### Zmiana Portu

Edytuj plik: `backend/app/src/main/resources/application.properties`

```properties
# Zmień z
server.port=8080

# Na
server.port=9090  # lub inny port
```

**Pamiętaj:** Zmień też URL w:
- Aplikacji Android: `app/build.gradle.kts` (ARUCO_API_BASE_URL)
- Postman: zmienna `baseUrl`

---

## 🐛 Troubleshooting

### Problem: Port 8080 już zajęty
```powershell
# Sprawdź co używa port
Get-NetTCPConnection -LocalPort 8080

# Zatrzymaj proces
./backend-manager.ps1 stop
```

### Problem: PowerShell nie pozwala wykonywać skryptów
```powershell
# Zezwól na wykonywanie skryptów
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Problem: Backend uruchamia się ale nie odpowiada
```powershell
# Sprawdź logi
./backend-manager.ps1 logs

# Lub czekaj dłużej (może trwać 30-60 sekund)
```

---

## 📞 Komunikacja Android ↔ Backend

### W Emulatorze Android:
```kotlin
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://10.0.2.2:8080/\"")
```

### Na Urządzeniu Fizycznym:
```kotlin
buildConfigField("String", "ARUCO_API_BASE_URL", "\"http://192.168.1.10:8080/\"")
// Zastąp 192.168.1.10 swoim IP
```

Jak znaleźć swoje IP:
```powershell
ipconfig
# Szukaj IPv4 Address (192.168.x.x lub podobne)
```

---

## 📚 Dodatkowe Dokumenty

1. **BACKEND_INSTRUKCJA.md** - Pełna instrukcja backendu
2. **INTEGRACJA_ANDROID.md** - Jak integrować z aplikacją Android
3. **postman/ArucoGeometryFeedback.postman_collection.json** - Kolekcja testów

---

## ✅ Checklist Połączenia

- [ ] Backend uruchomiony (`./backend-manager.ps1 status` = ✅)
- [ ] URL backendu: `http://localhost:8080`
- [ ] Aplikacja Android zbudowana z prawidłowym `ARUCO_API_BASE_URL`
- [ ] Urządzenie ma uprawnienia INTERNET w `AndroidManifest.xml`
- [ ] Test w Postmanie zadziałał
- [ ] Logi backendu pokazują request

---

**Version:** 1.0  
**Last Updated:** 2026-04-29  
**Author:** GitHub Copilot

