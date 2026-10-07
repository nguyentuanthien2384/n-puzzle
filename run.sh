#!/usr/bin/env sh
set -e
command -v mvn >/dev/null 2>&1 || { echo 'Không tìm thấy Maven. Hãy cài JDK 17+ và Apache Maven.'; exit 1; }
mvn clean javafx:run
