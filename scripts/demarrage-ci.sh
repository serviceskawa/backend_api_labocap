#!/usr/bin/env bash
# Démarre l'image livrée (même Dockerfile que la production, profil prod) et
# attend /actuator/health = UP. Appelé par ci.yml, donc avant tout déploiement.
#
# Pourquoi : le 03/10, la suite de tests était verte et la production refusait
# de démarrer — httpclient5 en portée test sortait du jar livré, et les tests ne
# construisent jamais NotificationsPush faute de compte Firebase. Ici on lui en
# donne un (faux, clé générée à la volée) pour que le chemin de production soit
# réellement parcouru.
#
# Ce que ce contrôle ne couvre pas : Flyway. Les migrations supposent le schéma
# Laravel du dump (baseline V50), absent du dépôt ; Hibernate crée le schéma.
#
# Usage : scripts/demarrage-ci.sh <image>   (ex. labo-anapath-api:ci)
set -euo pipefail
IMAGE="${1:?image à démarrer}"
NET=demarrage-ci
TMP=$(mktemp -d)
trap 'docker rm -f ci-db ci-api >/dev/null 2>&1 || true; docker network rm $NET >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TMP/cle.pem" 2>/dev/null
# Clé PEM sur une ligne, sauts de ligne échappés pour le JSON.
cle=$(awk '{printf "%s\\n", $0}' "$TMP/cle.pem")
cat > "$TMP/firebase.json" <<JSON
{"type":"service_account","project_id":"ci","private_key_id":"ci","private_key":"$cle",
 "client_email":"ci@ci.iam.gserviceaccount.com","client_id":"1","token_uri":"https://oauth2.googleapis.com/token"}
JSON
chmod 644 "$TMP/firebase.json"

docker network create $NET >/dev/null
docker run -d --name ci-db --network $NET -e POSTGRES_DB=labo -e POSTGRES_USER=labo \
  -e POSTGRES_PASSWORD=labo postgres:16 >/dev/null
until docker exec ci-db pg_isready -U labo -d labo >/dev/null 2>&1; do sleep 1; done

docker run -d --name ci-api --network $NET -p 127.0.0.1:7001:7001 \
  -v "$TMP/firebase.json:/var/lib/labo/secrets/firebase.json:ro" \
  -e SPRING_PROFILES_ACTIVE=prod -e PORT=7001 \
  -e DB_URL=jdbc:postgresql://ci-db:5432/labo -e DB_USERNAME=labo -e DB_PASSWORD=labo \
  -e SPRING_FLYWAY_ENABLED=false -e SPRING_JPA_HIBERNATE_DDL_AUTO=create \
  -e JWT_SECRET=ci-ci-ci-ci-ci-ci-ci-ci-ci-ci-ci-ci-ci-ci-ci-ci \
  -e MAIL_HOST=localhost -e MAIL_PORT=25 -e MAIL_FROM=ci@example.org \
  -e APP_FRONT_URL=http://localhost -e APP_CORS_ALLOWED_ORIGINS=http://localhost \
  -e PUBLIC_BASE_URL=http://localhost \
  -e PUSH_CREDENTIALS_FILE=/var/lib/labo/secrets/firebase.json \
  "$IMAGE" >/dev/null

for _ in $(seq 1 90); do
  if curl -fs http://127.0.0.1:7001/actuator/health | grep -q UP; then
    echo "Démarrage OK (profil prod, /actuator/health UP)"; exit 0
  fi
  [ "$(docker inspect -f '{{.State.Running}}' ci-api)" = true ] || break
  sleep 2
done
echo "::error::L'image livrée ne démarre pas — journal :"
docker logs --tail=120 ci-api
exit 1
