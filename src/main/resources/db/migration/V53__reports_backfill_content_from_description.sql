-- V53: Récupération du corps des comptes rendus historiques (conformité Laravel)
--
-- Laravel stocke le compte rendu dans `reports.description` (+ `description_micro`
-- pour la microscopie). La refonte lit `content` (+ `content_micro`), colonnes
-- ajoutées vides par V17 sans recopier les données. Résultat : les comptes
-- rendus antérieurs à la refonte s'affichaient VIDES dans l'app et les PDF.
--
-- On recopie la donnée là où la nouvelle colonne est vide. Idempotent
-- (WHERE content vide) : rejouable sans risque à chaque chargement d'un dump
-- Laravel du laboratoire.

-- Corps principal : description (Laravel) -> content (refonte)
UPDATE reports
SET content = description
WHERE (content IS NULL OR content = '')
  AND description IS NOT NULL
  AND description <> '';

-- Corps microscopie : description_micro (Laravel) -> content_micro (refonte).
-- La colonne source peut être absente selon le dump : on ne recopie que si
-- elle existe, pour ne pas casser la migration.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'reports' AND column_name = 'description_micro'
    ) THEN
        EXECUTE $sql$
            UPDATE reports
            SET content_micro = description_micro
            WHERE (content_micro IS NULL OR content_micro = '')
              AND description_micro IS NOT NULL
              AND description_micro <> ''
        $sql$;
    END IF;
END $$;
