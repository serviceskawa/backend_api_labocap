#!/usr/bin/env bash
#
# sync-laravel-storage.sh
# -----------------------------------------------------------------------------
# Copie les fichiers uploadés de l'ancienne application Laravel vers le dossier
# de stockage de l'API Java (Spring Boot).
#
# Contexte : lors de la migration, seule la BASE de données a été transférée.
# Les colonnes de type `attachment` (ex. docs.attachment = "documents/xxx.pdf")
# continuent de référencer des fichiers qui vivaient dans le `storage/app/public`
# de Laravel. L'API Java sert ces fichiers via GET /api/v1/files/<attachment>,
# résolus sous ${STORAGE_PATH}. Tant que les fichiers physiques ne sont pas
# présents à cet endroit, les téléchargements renvoient 404.
#
# Ce script recopie l'arborescence Laravel (documents/, settings/, ...) telle
# quelle sous STORAGE_PATH, en préservant les chemins relatifs stockés en base.
#
# Idempotent : par défaut il n'écrase PAS un fichier déjà présent côté Java
# (--ignore-existing), pour ne jamais clobberer un fichier uploadé via l'API.
#
# Usage :
#   ./scripts/sync-laravel-storage.sh [SRC_LARAVEL_PUBLIC] [DEST_STORAGE_PATH]
#
#   SRC_LARAVEL_PUBLIC  chemin du `storage/app/public` de Laravel
#                       (défaut : ../labo-anapath-main/storage/app/public)
#   DEST_STORAGE_PATH   racine de stockage de l'API Java
#                       (défaut : $STORAGE_PATH, sinon lu depuis .env, sinon ./storage)
#
# Exemples :
#   # dev (défauts)
#   ./scripts/sync-laravel-storage.sh
#   # prod
#   ./scripts/sync-laravel-storage.sh /var/www/laravel/storage/app/public /var/lib/labo/storage
# -----------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$(dirname "$SCRIPT_DIR")"

# --- Source (storage public Laravel) ---------------------------------------
SRC="${1:-$API_DIR/../labo-anapath-main/storage/app/public}"

# --- Destination (STORAGE_PATH de l'API Java) ------------------------------
DEST="${2:-}"
if [[ -z "$DEST" ]]; then
  DEST="${STORAGE_PATH:-}"
fi
if [[ -z "$DEST" && -f "$API_DIR/.env" ]]; then
  # lit STORAGE_PATH depuis .env (ligne STORAGE_PATH=...)
  DEST="$(grep -E '^STORAGE_PATH=' "$API_DIR/.env" | head -1 | cut -d= -f2- || true)"
fi
DEST="${DEST:-$API_DIR/storage}"

if [[ ! -d "$SRC" ]]; then
  echo "ERREUR : dossier source introuvable : $SRC" >&2
  exit 1
fi

echo "Source (Laravel) : $SRC"
echo "Destination (API Java) : $DEST"
mkdir -p "$DEST"

# Copie récursive en préservant l'arborescence.
#   --ignore-existing : ne touche pas aux fichiers déjà présents côté Java
#   --exclude .gitignore : artefacts Laravel inutiles
#   --prune-empty-dirs : évite de créer des dossiers vides
rsync -av \
  --ignore-existing \
  --exclude '.gitignore' \
  --prune-empty-dirs \
  "$SRC"/ "$DEST"/

echo
echo "Terminé. Fichiers présents côté API Java :"
find "$DEST" -type f | sed "s|$DEST/||" | sort
