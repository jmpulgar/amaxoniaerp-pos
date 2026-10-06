#!/bin/bash
set -e

PROJECT_DIR="/home/amaxonia-pos"
BACKEND_DIR="$PROJECT_DIR/amaxoniaerp-backend"
SERVICE_NAME="amaxoniaerp-backend"
BRANCH="main"

echo "==> Entrando a $PROJECT_DIR"
cd "$PROJECT_DIR"

echo "==> Git pull"
git checkout "$BRANCH"
git pull origin "$BRANCH"

echo "==> Build backend"
cd "$BACKEND_DIR"
# Stop leftover Gradle/Kotlin daemons from earlier builds: on this 4 GB server they get OOM-killed.
./gradlew --stop || true
pkill -f "kotlin-compiler-embeddable.*KotlinCompileDaemon" || true
./gradlew build --no-daemon

echo "==> Restart servicio"
systemctl restart "$SERVICE_NAME"

echo "==> Status"
systemctl status "$SERVICE_NAME" --no-pager

echo "==> Deploy OK"
