#!/usr/bin/env bash
#
# Rattrapage des opérations de caisse manquantes.
#
# POURQUOI. Jusqu'au correctif cd73386, le règlement d'une facture créditait le
# solde de la caisse sans écrire d'opération. Or l'écran de fermeture additionne
# les opérations, pas le solde : les journées concernées affichent zéro et ne
# sont pas clôturables. Ce script reconstitue les opérations manquantes à partir
# des factures, qui portent le montant, le mode de paiement et l'horodatage.
#
# CE QU'IL NE FAIT PAS. Il ne touche à aucun solde. Les factures ont déjà
# crédité la caisse au moment du paiement ; n'écrire que les opérations évite
# tout double comptage. Il ne remonte pas au-delà de la journée demandée.
#
# USAGE
#   ./rattrapage-operations-caisse.sh <conteneur> [jour]          → rapport seul
#   ./rattrapage-operations-caisse.sh <conteneur> [jour] --ecrire → écrit
#
#   <conteneur> : nom du conteneur PostgreSQL (ex. labocap-db)
#   [jour]      : date au format AAAA-MM-JJ, par défaut aujourd'hui
#
# Sans --ecrire, RIEN n'est modifié : le script se contente de chiffrer.
#
set -euo pipefail

CONTENEUR="${1:-}"
JOUR="${2:-}"
MODE="${3:-}"

if [ -z "$CONTENEUR" ]; then
    echo "Usage : $0 <conteneur> [AAAA-MM-JJ] [--ecrire]" >&2
    exit 1
fi

# Le deuxième argument peut être la date ou directement --ecrire.
if [ "$JOUR" = "--ecrire" ]; then
    MODE="--ecrire"
    JOUR=""
fi
[ -z "$JOUR" ] && JOUR=$(date +%F)

case "$JOUR" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
    *) echo "Date attendue au format AAAA-MM-JJ, reçu : $JOUR" >&2; exit 1 ;;
esac

# Le SQL arrive par l'entrée standard : psql n'interpole pas ses variables dans
# les commandes passées par -c, et la date est de toute façon déjà résolue ici.
sql() {
    docker exec -i "$CONTENEUR" \
        sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
}

# ---------------------------------------------------------------------------
# Sélection commune au rapport et à l'écriture.
#
# `updated_at` fait foi comme instant du règlement : c'est la sauvegarde opérée
# par markAsPaid. `status_invoice = 0` écarte les avoirs, qui débitent la caisse
# de dépense et n'ont rien à faire dans une fermeture de caisse de vente.
# ---------------------------------------------------------------------------
SELECTION="
    FROM invoices i
    WHERE i.deleted_at IS NULL
      AND i.paid = true
      AND i.status_invoice = 0
      AND DATE(i.updated_at) = DATE('$JOUR')
      AND COALESCE(i.payment, '') <> ''
      AND NOT EXISTS (
            SELECT 1 FROM cashbox_operations o
            WHERE o.invoice_id = i.id AND o.deleted_at IS NULL)"

echo "═══ Rattrapage des opérations de caisse — journée du $JOUR ═══"
echo
echo "── Factures réglées sans opération"
sql <<SQL
SELECT i.branch_id AS branche,
       i.payment   AS mode,
       count(*)    AS factures,
       sum(i.total)::bigint AS montant
$SELECTION
GROUP BY i.branch_id, i.payment
ORDER BY i.branch_id, montant DESC;
SQL

echo
echo "── Sessions de caisse ouvertes (leur horodatage borne la somme)"
sql <<SQL
SELECT d.branch_id AS branche, d.code, d.updated_at AS depuis
FROM cashbox_dailies d
WHERE d.status = 1 AND d.deleted_at IS NULL
ORDER BY d.branch_id, d.updated_at DESC;
SQL

echo
echo "── Factures antérieures à l'ouverture de session (resteraient invisibles)"
sql <<SQL
SELECT i.branch_id AS branche, count(*) AS factures, sum(i.total)::bigint AS montant
$SELECTION
  AND i.updated_at < (
        SELECT max(d.updated_at) FROM cashbox_dailies d
        WHERE d.status = 1 AND d.deleted_at IS NULL AND d.branch_id = i.branch_id)
GROUP BY i.branch_id;
SQL

if [ "$MODE" != "--ecrire" ]; then
    echo
    echo "Rapport seul — rien n'a été modifié."
    echo "Pour écrire :  $0 $CONTENEUR $JOUR --ecrire"
    exit 0
fi

echo
echo "── Écriture"

# La caisse retenue est celle de vente au solde le plus élevé : c'est la caisse
# active, les doublons de migration étant vides. Ce choix n'influe pas sur le
# total de fermeture (la somme ne filtre que sur la branche), il ne sert qu'à
# rattacher l'opération à une caisse cohérente.
sql <<SQL
BEGIN;

INSERT INTO cashbox_operations
    (id, branch_id, cashbox_id, amount, type, payment_method, invoice_id,
     operation_date, description, created_at, updated_at)
SELECT gen_random_uuid(),
       i.branch_id,
       (SELECT c.id FROM cashboxes c
         WHERE c.type = 'vente' AND c.branch_id = i.branch_id
         ORDER BY c.balance DESC LIMIT 1),
       i.total,
       'CREDIT',
       i.payment,
       i.id,
       DATE(i.updated_at),
       'Règlement facture ' || COALESCE(i.code, '') || ' (rattrapage)',
       i.updated_at,
       now()
$SELECTION;

COMMIT;
SQL

echo
echo "── Vérification après écriture"
sql <<SQL
SELECT o.branch_id AS branche, o.payment_method AS mode,
       count(*) AS operations, sum(o.amount)::bigint AS montant
FROM cashbox_operations o
WHERE o.deleted_at IS NULL
  AND o.description LIKE '%(rattrapage)'
  AND DATE(o.created_at) = DATE('$JOUR')
GROUP BY o.branch_id, o.payment_method
ORDER BY o.branch_id, montant DESC;
SQL

echo
echo "── Reste-t-il des factures sans opération ?"
sql <<SQL
SELECT count(*) AS restantes $SELECTION;
SQL

echo
echo "Terminé. Pour annuler ce rattrapage :"
echo "  DELETE FROM cashbox_operations"
echo "   WHERE description LIKE '%(rattrapage)' AND DATE(created_at) = DATE('$JOUR');"
