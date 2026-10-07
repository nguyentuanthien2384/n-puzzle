@echo off
where mvn >nul 2>nul
if errorlevel 1 (
  echo [LOI] Khong tim thay Maven.
  pause
  exit /b 1
)
mvn test
pause
