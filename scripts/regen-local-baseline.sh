#!/usr/bin/env bash
# Régénère la baseline locale (db/local/V1__baseline_from_entities.sql) depuis
# les entités JPA.
#
# À relancer après tout ajout ou modification d'entité : sinon le profil `local`
# échoue au démarrage, `ddl-auto: validate` détectant l'écart entre la baseline
# et les entités.
#
# Principe : Hibernate crée le schéma (`ddl-auto: create`) sur une base jetable,
# puis `pg_dump --schema-only` en extrait le DDL PostgreSQL réel.
#
#   ./scripts/regen-local-baseline.sh
set -euo pipefail

cd "$(dirname "$0")/.."

IMAGE=${IMAGE:-labo-anapath-api:dev}
NET=baseline-gen
DB=baseline-db
APP=baseline-app
OUT=src/main/resources/db/local/V1__baseline_from_entities.sql

cleanup() {
  docker rm -f "$APP" "$DB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker image inspect "$IMAGE" >/dev/null 2>&1 || {
  echo "✗ Image $IMAGE absente. La construire d'abord :"
  echo "    cd ../labocap-stack && docker compose build api"
  exit 1
}

cleanup
docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" \
  -e POSTGRES_PASSWORD=x -e POSTGRES_DB=gen postgres:16 >/dev/null

echo "→ attente de PostgreSQL…"
for _ in $(seq 1 40); do
  docker exec "$DB" pg_isready -U postgres -d gen >/dev/null 2>&1 && break
  sleep 1
done

echo "→ génération du schéma par Hibernate…"
# Flyway désactivé et ddl-auto=create : Hibernate fait autorité sur le schéma.
docker run -d --name "$APP" --network "$NET" \
  -e DB_URL=jdbc:postgresql://$DB:5432/gen \
  -e DB_USERNAME=postgres -e DB_PASSWORD=x \
  -e JWT_SECRET=regenregenregenregenregenregenregenregen \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_FLYWAY_ENABLED=false \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=create \
  -e MAIL_HOST=localhost -e MAIL_PORT=2525 \
  -e MAIL_FROM=x@x.tld -e MAIL_FROM_NAME=x \
  -e STORAGE_PATH=/tmp/storage \
  "$IMAGE" >/dev/null

for _ in $(seq 1 60); do
  N=$(docker exec "$DB" psql -U postgres -d gen -tAc \
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null | tr -d ' ')
  [ "${N:-0}" -gt 50 ] && break
  sleep 1
done

docker logs "$APP" 2>&1 | grep -q "Started AnaPathApplication" || {
  echo "✗ L'application n'a pas démarré — schéma incomplet, baseline non régénérée."
  docker logs "$APP" 2>&1 | grep -iE "error|caused by" | head -5
  exit 1
}

TABLES=$(docker exec "$DB" psql -U postgres -d gen -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';" | tr -d ' ')
echo "→ $TABLES tables créées"

# En-tête conservé à l'identique, seul le corps DDL est remplacé.
awk '/^-- ─+$/{c++} c>=2{print; if(c>=2 && /^-- ─+$/ && ++e==1) exit}' "$OUT" >/dev/null 2>&1 || true
sed -n '1,/^-- ─\{20,\}$/p' "$OUT" > /tmp/baseline-header.sql 2>/dev/null
# Recompose : tout ce qui précède la première instruction SQL
sed -n '1,/^$/p' "$OUT" | head -n -1 > /tmp/baseline-header.sql

TMP=$(mktemp)
cat /tmp/baseline-header.sql > "$TMP"
echo >> "$TMP"
docker exec "$DB" pg_dump -U postgres -d gen \
  --schema-only --no-owner --no-privileges --no-comments 2>/dev/null \
  | grep -vE '^\\|^SET |^SELECT pg_catalog\.set_config|^--' \
  | cat -s >> "$TMP"

mv "$TMP" "$OUT"
echo "✓ $OUT régénéré ($(grep -c 'CREATE TABLE' "$OUT") tables)"
echo
echo "  Reconstruire l'image puis repartir d'une base vierge :"
echo "    cd ../labocap-stack && docker compose build api && docker compose down -v && docker compose up -d"
