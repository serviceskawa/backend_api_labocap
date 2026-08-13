#!/usr/bin/env bash
#
# État des clôtures de caisse affectées par deux défauts corrigés le 13/08/2026.
#
# CE QU'IL CHERCHE
#
#   1. Le solde de fermeture ne retenait que les espèces comptées. Il doit
#      valoir le TOTAL DES FACTURES réglées pendant la vie de la session
#      (`total_calculated`) — règle retenue par le client, qui s'écarte du
#      legacy : celui-ci enregistrait ce que le caissier avait compté. Une
#      journée encaissée en Mobile Money enregistrait un solde nul.
#
#   2. L'écart était calculé `calculé − compté`, à l'envers du legacy
#      (`compté − calculé`). Or la fermeture AJOUTE cet écart au solde de la
#      caisse : un signe inversé l'a déplacé dans le mauvais sens à chaque
#      clôture.
#
# CE QU'IL NE FAIT PAS
#
#   Rien d'autre que lire. Aucune écriture, aucune correction. Les clôtures
#   passées sont figées en base ; ce script sert à décider s'il faut les
#   reprendre, et de combien.
#
# PÉRIODE EXAMINÉE
#
#   Seules les clôtures postérieures à la bascule sont retenues. Une version
#   antérieure de ce script n'en posait aucune : elle remontait des journées de
#   2023, dont les valeurs viennent de Laravel et n'ont rien à voir avec les
#   défauts corrigés ici. Je croyais que les formules d'origine suffiraient à
#   écarter le legacy — elles ne le font pas, la saisie manuelle de l'époque
#   ayant laissé des écarts que ces formules ne reproduisent pas.
#
# USAGE
#   ./etat-clotures-caisse.sh <conteneur> [bascule]
#
#   [bascule] : date de mise en service, AAAA-MM-JJ (défaut 2026-08-08)
#
set -euo pipefail

CONTENEUR="${1:-}"
BASCULE="${2:-2026-08-08}"
if [ -z "$CONTENEUR" ]; then
    echo "Usage : $0 <conteneur-postgres> [AAAA-MM-JJ]" >&2
    exit 1
fi

case "$BASCULE" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
    *) echo "Date attendue au format AAAA-MM-JJ, reçu : $BASCULE" >&2; exit 1 ;;
esac

sql() {
    docker exec -i "$CONTENEUR" \
        sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
}

echo "═══ État des clôtures de caisse — depuis la bascule du $BASCULE ═══"
echo

echo "── 1. Vue d'ensemble"
sql <<'SQL'
SELECT count(*) FILTER (WHERE status = 0) AS cloturees,
       count(*) FILTER (WHERE status = 1) AS encore_ouvertes,
       count(*) AS total
FROM cashbox_dailies
WHERE deleted_at IS NULL;
SQL

echo
echo "── 2. Soldes de fermeture à reprendre"
echo "   (solde enregistré ≠ total des factures de la période)"
sql <<SQL
SELECT code,
       date,
       opening_balance::bigint          AS fond_initial,
       cash_confirmation::bigint        AS especes_comptees,
       total_confirmation::bigint       AS total_compte,
       total_calculated::bigint         AS total_facture,
       closing_balance::bigint          AS solde_enregistre,
       COALESCE(total_calculated,0)::bigint AS solde_attendu,
       (COALESCE(total_calculated,0) - COALESCE(closing_balance,0))::bigint AS manquant
FROM cashbox_dailies
WHERE deleted_at IS NULL
  AND status = 0
  AND date >= DATE '$BASCULE'
  AND COALESCE(closing_balance,0) IS DISTINCT FROM COALESCE(total_calculated,0)
ORDER BY date DESC
LIMIT 40;
SQL

echo
echo "── 3. Écarts enregistrés à l'envers"
echo "   (écart stocké ≠ total compté − total calculé)"
sql <<SQL
SELECT code,
       date,
       total_calculated::bigint   AS calcule,
       total_confirmation::bigint AS compte,
       total_ecart::bigint        AS ecart_enregistre,
       (COALESCE(total_confirmation,0) - COALESCE(total_calculated,0))::bigint AS ecart_attendu
FROM cashbox_dailies
WHERE deleted_at IS NULL
  AND status = 0
  AND date >= DATE '$BASCULE'
  AND COALESCE(total_ecart,0)
      IS DISTINCT FROM (COALESCE(total_confirmation,0) - COALESCE(total_calculated,0))
ORDER BY date DESC
LIMIT 40;
SQL

echo
echo "── 4. Dérive cumulée du solde des caisses"
echo "   Chaque clôture ajoute son écart au solde de la caisse. Ce total est"
echo "   donc ce dont les soldes se sont éloignés du fait du signe inversé."
sql <<SQL
SELECT count(*) AS clotures_concernees,
       SUM((COALESCE(total_confirmation,0) - COALESCE(total_calculated,0))
           - COALESCE(total_ecart,0))::bigint AS derive_totale
FROM cashbox_dailies
WHERE deleted_at IS NULL
  AND status = 0
  AND date >= DATE '$BASCULE'
  AND COALESCE(total_ecart,0)
      IS DISTINCT FROM (COALESCE(total_confirmation,0) - COALESCE(total_calculated,0));
SQL

echo
echo "── 5. Solde actuel des caisses, pour mémoire"
sql <<'SQL'
SELECT COALESCE(NULLIF(name,''), '(sans nom)') AS caisse,
       type,
       statut,
       balance::bigint         AS solde,
       opening_balance::bigint AS fond_ouverture
FROM cashboxes
ORDER BY type, balance DESC;
SQL

echo
echo "Lecture seule — rien n'a été modifié."
