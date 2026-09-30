-- Module Biologie clinique : une même chaîne (catalogue, bons, comptes-rendus)
-- sert désormais deux disciplines. Chaque ligne existante est de l'anatomie
-- pathologique ; la valeur par défaut les classe toutes en PATHOLOGY.
--
-- Sur PostgreSQL 11+, ADD COLUMN ... NOT NULL DEFAULT <constante> ne réécrit pas
-- la table : l'ajout de colonne est instantané, même sur test_orders et reports.
-- Seules les contraintes CHECK et les index parcourent les tables, le temps de
-- la migration (quelques secondes aux volumes actuels).

ALTER TABLE lab_tests      ADD COLUMN IF NOT EXISTS discipline VARCHAR(20) NOT NULL DEFAULT 'PATHOLOGY';
ALTER TABLE category_tests ADD COLUMN IF NOT EXISTS discipline VARCHAR(20) NOT NULL DEFAULT 'PATHOLOGY';
ALTER TABLE test_orders    ADD COLUMN IF NOT EXISTS discipline VARCHAR(20) NOT NULL DEFAULT 'PATHOLOGY';
-- Recopiée depuis le bon à la création du compte-rendu : plusieurs requêtes sur
-- reports ne joignent pas test_orders et doivent pouvoir filtrer seules.
ALTER TABLE reports        ADD COLUMN IF NOT EXISTS discipline VARCHAR(20) NOT NULL DEFAULT 'PATHOLOGY';

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['lab_tests', 'category_tests', 'test_orders', 'reports'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'chk_' || t || '_discipline'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I CHECK (discipline IN (''PATHOLOGY'', ''BIOLOGY''))',
                t, 'chk_' || t || '_discipline');
        END IF;
    END LOOP;
END $$;

-- Les listes et compteurs filtrent désormais par branche, discipline et statut.
CREATE INDEX IF NOT EXISTS idx_test_orders_branch_discipline_status
    ON test_orders (branch_id, discipline, status);
CREATE INDEX IF NOT EXISTS idx_reports_branch_discipline_status
    ON reports (branch_id, discipline, status);
