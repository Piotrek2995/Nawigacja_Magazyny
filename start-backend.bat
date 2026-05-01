@echo off
REM Start the backend server
echo Starting Aruco Backend Server...
cd /d "%~dp0backend"
call gradlew :app:bootRun
pause

