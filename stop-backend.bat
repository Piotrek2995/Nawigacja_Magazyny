@echo off
REM Stop the backend server running on port 8080
echo Stopping Aruco Backend Server (port 8080)...
for /f "tokens=5" %%a in ('netstat -ano ^| find ":8080"') do (
    taskkill /PID %%a /F
    echo Process %%a killed.
)
echo Backend server stopped.
pause

