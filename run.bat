@echo off
where mvn >nul 2>nul
if errorlevel 1 (
  echo [LOI] Khong tim thay Maven. Hay cai JDK 17+ va Apache Maven, sau do mo terminal moi.
  pause
  exit /b 1
)
mvn clean javafx:run
pause
