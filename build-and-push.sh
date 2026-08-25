#!/usr/bin/env bash
# build-and-push.sh — SkillUp / Team Elevate
# Atelier 2: Application multi-conteneurs
#
# NOTE: Dockerfiles are now multi-stage — Maven builds happen inside Docker.
# This script is only needed if you want to push pre-built images to DockerHub.
# For local dev just run: docker compose up -d --build
#
# Usage:
#   chmod +x build-and-push.sh
#   docker login                 ← do this once before running the script
#   ./build-and-push.sh

set -euo pipefail

# Resolve project root (directory containing this script)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=================================================="
echo " SkillUp — Multi-container Build & Push"
echo " Project root : $SCRIPT_DIR"
echo "=================================================="

# ──────────────────────────────────────────────────────
# STEP 1 — Compile JARs for every Java microservice
# (Dockerfiles are single-stage and expect target/*.jar)
# ──────────────────────────────────────────────────────
declare -A SERVICES=(
  ["eureka"]="backEnd/eureka"
  ["config-server"]="backEnd/config-server"
  ["gateway"]="backEnd/gateway"
  ["user"]="backEnd/microservices/user"
  ["quiz"]="backEnd/microservices/Quiz"
  ["forum"]="backEnd/microservices/forum"
  ["course"]="backEnd/microservices/Course"
  ["formation"]="backEnd/microservices/Formation"
  ["certificat"]="backEnd/microservices/certificat"
  ["eventgestion"]="backEnd/microservices/eventGestion"
  ["job-offer"]="backEnd/microservices/job-offer"
  ["payment"]="backEnd/microservices/Payment"
  ["ticket"]="backEnd/microservices/Ticket"
)

# Build in a fixed order (dependencies first)
ORDERED=(
  "eureka:backEnd/eureka"
  "config-server:backEnd/config-server"
  "gateway:backEnd/gateway"
  "user:backEnd/microservices/user"
  "quiz:backEnd/microservices/Quiz"
  "forum:backEnd/microservices/forum"
  "course:backEnd/microservices/Course"
  "formation:backEnd/microservices/Formation"
  "certificat:backEnd/microservices/certificat"
  "eventgestion:backEnd/microservices/eventGestion"
  "job-offer:backEnd/microservices/job-offer"
  "payment:backEnd/microservices/Payment"
  "ticket:backEnd/microservices/Ticket"
  "entreprise:backEnd/microservices/entreprise"
  "internship-service:backEnd/microservices/internship-service"
)

echo ""
echo "──────────────────────────────────────────────────"
echo " STEP 1/3 — Compiling JARs (mvnw clean package)"
echo "──────────────────────────────────────────────────"

for entry in "${ORDERED[@]}"; do
  NAME="${entry%%:*}"
  DIR="${entry##*:}"
  ABS_DIR="${SCRIPT_DIR}/${DIR}"

  if [ ! -d "$ABS_DIR" ]; then
    echo "[WARN] Directory not found, skipping JAR build: ${ABS_DIR}"
    continue
  fi

  echo ""
  echo "  → Building JAR: ${NAME} (${DIR})"
  (cd "$ABS_DIR" && "$SCRIPT_DIR/backEnd/mvnw" clean package -DskipTests --no-transfer-progress)
  echo "  ✓ ${NAME} JAR ready"
done

# ──────────────────────────────────────────────────────
# STEP 2 — docker compose build (build all Docker images)
# ──────────────────────────────────────────────────────
echo ""
echo "──────────────────────────────────────────────────"
echo " STEP 2/3 — docker compose build"
echo "──────────────────────────────────────────────────"
cd "$SCRIPT_DIR"
docker compose build

# ──────────────────────────────────────────────────────
# STEP 3 — docker compose push (push images to DockerHub)
# ──────────────────────────────────────────────────────
echo ""
echo "──────────────────────────────────────────────────"
echo " STEP 3/3 — docker compose push"
echo " NB: This step may take longer than usual."
echo "──────────────────────────────────────────────────"
docker compose push

echo ""
echo "=================================================="
echo " All images pushed successfully to DockerHub!"
echo ""
echo " To start the full stack:"
echo "   docker compose up -d"
echo ""
echo " To verify:"
echo "   docker compose ps"
echo "   docker ps"
echo ""
echo " Access points:"
echo "   Eureka   → http://localhost:8761"
echo "   Gateway  → http://localhost:9090"
echo "   Keycloak → http://localhost:8099  (admin/admin)"
echo "   Frontend → cd skillup_front && npm start"
echo "=================================================="
