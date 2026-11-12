@echo off
REM Wymaga zainstalowanego JDK 21 i Gradle 8.8+ (w PATH)
gradle build
echo.
echo Gotowy plik: build\libs\chestbot-1.0.0.jar
pause
