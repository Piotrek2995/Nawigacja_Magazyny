# Backend Management Script for Aruco Geometry Feedback
# Użycie: .\backend-manager.ps1 [start|stop|status|restart]

param(
    [ValidateSet("start", "stop", "status", "restart", "logs")]
    [string]$Action = "status"
)

$BackendPath = Join-Path (Get-Location) "backend"
$ProcessName = "java"
$Port = 8080

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host "=" * 60
    Write-Host $Message
    Write-Host "=" * 60
    Write-Host ""
}

function Start-Backend {
    Write-Header "🟢 Uruchamianie Backend'u ArUco"

    if (Test-Path -Path $BackendPath) {
        Write-Host "📁 Ścieżka backendu: $BackendPath"
        Write-Host "⏳ Uruchamianie serwera..."

        Push-Location $BackendPath
        & ".\gradlew" :app:bootRun
        Pop-Location
    } else {
        Write-Host "❌ Nie znaleziono folderu backend w: $BackendPath"
    }
}

function Stop-Backend {
    Write-Header "🔴 Zatrzymywanie Backend'u"

    $process = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue

    if ($process) {
        Write-Host "🔍 Znaleziono proces nasłuchujący na porcie $Port"
        foreach ($item in $process) {
            $pid = $item.OwningProcess
            Write-Host "🛑 Zatrzymywanie procesu PID: $pid"
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        }
        Write-Host "✅ Backend zatrzymany"
    } else {
        Write-Host "❌ Brak procesu nasłuchującego na porcie $Port"
    }
}

function Check-Status {
    Write-Header "📊 Status Backend'u"

    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue

    if ($connection) {
        Write-Host "✅ Backend URUCHOMIONY na porcie $Port"
        Write-Host ""
        Write-Host "📍 Informacje:"
        Write-Host "   Adres: localhost:$Port"
        Write-Host "   API URL: http://localhost:$Port/api/v1/aruco/geometry-feedback"
        Write-Host ""

        # TestHealth Check
        try {
            $response = Invoke-RestMethod -Uri "http://localhost:$Port/actuator/health" -Method Get -ErrorAction SilentlyContinue
            Write-Host "💚 Health Status: $($response.status)"
        } catch {
            Write-Host "⚠️ Nie można sprawdzić health check'u"
        }
    } else {
        Write-Host "❌ Backend ZATRZYMANY"
        Write-Host ""
        Write-Host "Aby uruchomić backend, wykonaj:"
        Write-Host "   .\backend-manager.ps1 start"
    }
}

function Restart-Backend {
    Write-Header "🔄 Restart Backend'u"
    Stop-Backend
    Start-Sleep -Seconds 2
    Start-Backend
}

function Show-Logs {
    Write-Header "📋 Logi Backend'u"

    $logsPath = Join-Path $BackendPath "app/build/reports"

    if (Test-Path -Path $logsPath) {
        Write-Host "📁 Folder logów: $logsPath"
        Get-ChildItem -Path $logsPath -Recurse -File | Select-Object FullName -Last 10
    } else {
        Write-Host "❌ Brak dostępnych logów"
    }
}

function Show-Help {
    Write-Header "🆘 Pomoc - Backend Manager"

    Write-Host "Użycie: .\backend-manager.ps1 [ACTION]"
    Write-Host ""
    Write-Host "Dostępne akcje:"
    Write-Host ""
    Write-Host "  start    - Uruchomia backend serwer"
    Write-Host "  stop     - Zatrzymuje backend serwer"
    Write-Host "  status   - Sprawdza status backendu (domyślne)"
    Write-Host "  restart  - Restartuje backend"
    Write-Host "  logs     - Wyświetla logi backendu"
    Write-Host ""
    Write-Host "Przykłady:"
    Write-Host "  .\backend-manager.ps1 start"
    Write-Host "  .\backend-manager.ps1 stop"
    Write-Host "  .\backend-manager.ps1 status"
    Write-Host ""
}

# Main
switch ($Action.ToLower()) {
    "start" {
        Start-Backend
    }
    "stop" {
        Stop-Backend
    }
    "status" {
        Check-Status
    }
    "restart" {
        Restart-Backend
    }
    "logs" {
        Show-Logs
    }
    "help" {
        Show-Help
    }
    default {
        Check-Status
    }
}

Write-Host ""

