#!/bin/bash
set -e

PROJECT_DIR="/home/amaxonia-pos"
SERVICE_NAME="amaxoniaerp-backend"

echo "==> Entrando a $PROJECT_DIR"
cd "$PROJECT_DIR"

echo "==> Haciendo git pull"
git pull

echo "==> Reiniciando servicio $SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

echo "==> Verificando estado"
systemctl status "$SERVICE_NAME" --no-pager

echo "==> Deploy completado"
