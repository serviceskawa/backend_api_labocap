#!/bin/sh
# Disponibilité mensuelle en heures ouvrées (lundi–vendredi 7 h 30–18 h 30,
# samedi 8 h–13 h, heure de Porto-Novo), calculée depuis la base SQLite
# d'Uptime Kuma. À lancer sur la machine de supervision, où le dossier de
# données d'Uptime Kuma est monté (voir docs/supervision.md).
#
#   ./disponibilite-mensuelle.sh 2026-10 [chemin/vers/kuma.db]
#
# Uptime Kuma horodate en UTC ; Porto-Novo est à UTC+1 toute l'année, d'où le
# décalage d'une heure. Statuts : 1 = UP, 0 = DOWN ; les sondes « en attente »
# (2) et « maintenance » (3) sont ignorées.
set -eu
MOIS="${1:?mois attendu, ex. 2026-10}"
BASE="${2:-./uptime-kuma-data/kuma.db}"
DEBUT="$MOIS-01"
FIN=$(date -d "$DEBUT +1 month" +%Y-%m-%d 2>/dev/null || date -v+1m -j -f %Y-%m-%d "$DEBUT" +%Y-%m-%d)

sqlite3 -header -column "$BASE" "
WITH h AS (
  SELECT monitor_id, status, datetime(time, '+1 hour') AS t
  FROM heartbeat
  WHERE status IN (0, 1)
    AND datetime(time, '+1 hour') >= '$DEBUT' AND datetime(time, '+1 hour') < '$FIN'
)
SELECT m.name AS moniteur,
       COUNT(*) AS sondes,
       SUM(h.status = 0) AS echecs,
       ROUND(100.0 * SUM(h.status = 1) / COUNT(*), 2) AS disponibilite_pct
FROM h JOIN monitor m ON m.id = h.monitor_id
WHERE (strftime('%w', t) BETWEEN '1' AND '5' AND time(t) BETWEEN '07:30:00' AND '18:30:00')
   OR (strftime('%w', t) = '6' AND time(t) BETWEEN '08:00:00' AND '13:00:00')
GROUP BY m.name
ORDER BY m.name;"
