@echo off
rem CLI cua N-Puzzle Research Platform. Tren Windows hay viet bang bang dau phay (khong co khoang trang):
rem   cli.bat list
rem   cli.bat solve --board 1,2,3,4,5,6,0,7,8 --algo ida --heuristic linear-conflict
rem   cli.bat verify
rem   cli.bat benchmark --dataset random-15p --algos astar,ida --heuristics manhattan,apdb --reps 3
where mvn >nul 2>nul
if errorlevel 1 (
  echo [LOI] Khong tim thay Maven. Hay cai JDK 17+ va Apache Maven.
  exit /b 1
)
call mvn -q compile exec:java -Dexec.args="%*"
