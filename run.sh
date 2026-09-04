#!/usr/bin/env bash
# Lance l'API avec la configuration du .env, relue à chaque fois.
#
# Pourquoi ce script : « set -a; source .env » exporte les valeurs dans le
# shell, où elles restent. Relancer Maven depuis ce même terminal réutilise
# alors l'ancienne configuration, et une modification du .env passe inaperçue —
# on croit tester le nouveau réglage alors qu'on rejoue l'ancien.
set -euo pipefail
cd "$(dirname "$0")"

set -a
# shellcheck disable=SC1091
source .env
set +a

echo "─────────────────────────────────────────────────────────"
echo " Passerelle SMS : ${FLUIDPAY_SMS_BASE_URL:-(non définie)}"
echo " Clé API        : ${FLUIDPAY_SMS_API_KEY:0:12}…"
echo " Lien public    : ${PUBLIC_BASE_URL:-(défaut https://api.caap.bj)}"
case "${FLUIDPAY_SMS_BASE_URL:-}" in
  *localhost*|*127.0.0.1*) echo " Mode           : ESSAI À BLANC (faux serveur, aucun SMS réel)" ;;
  *)                       echo " Mode           : ⚠ ENVOIS RÉELS" ;;
esac
echo "─────────────────────────────────────────────────────────"

exec mvn spring-boot:run -Dmaven.test.skip=true
