#!/bin/bash
# Lancement dev du backend : charge .env puis spring-boot:run (tests ignorés).
set -a
. /home/kawa/Bureau/projects/refont_labo/labo-anapath-api/.env
set +a
export PATH="/usr/bin:/bin:/usr/local/bin:$PATH"
cd /home/kawa/Bureau/projects/refont_labo/labo-anapath-api
exec /usr/bin/mvn spring-boot:run -Dmaven.test.skip=true
