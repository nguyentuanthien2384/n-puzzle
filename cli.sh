#!/usr/bin/env sh
# CLI của N-Puzzle Research Platform. Ví dụ:
#   ./cli.sh list
#   ./cli.sh solve --board "1 2 3 4 5 6 0 7 8" --algo ida --heuristic linear-conflict
#   ./cli.sh verify
#   ./cli.sh benchmark --dataset random-15p --algos astar,ida --heuristics manhattan,apdb --reps 3
set -e
command -v mvn >/dev/null 2>&1 || { echo 'Không tìm thấy Maven. Hãy cài JDK 17+ và Apache Maven.'; exit 1; }
args=""
for a in "$@"; do
  case "$a" in
    *" "*) args="$args \"$a\"" ;;
    *) args="$args $a" ;;
  esac
done
mvn -q compile exec:java -Dexec.args="$args"
