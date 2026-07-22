-- Conformité module Utilisateur avec l'app Laravel :
--  - colonnes commission (%) et whatsapp sur users ;
--  - affectation multi-branches via le pivot branch_user (comme Laravel).
-- La colonne users.branch_id reste la branche « d'attache » qui pilote
-- l'isolation des données ; branch_user liste les branches accessibles.

ALTER TABLE users ADD COLUMN IF NOT EXISTS commission NUMERIC(10,2) DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS whatsapp   VARCHAR(255);

CREATE TABLE IF NOT EXISTS branch_user (
    user_id   UUID NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, branch_id)
);

-- Backfill : chaque utilisateur est rattaché à sa branche courante.
INSERT INTO branch_user (user_id, branch_id)
SELECT id, branch_id FROM users WHERE branch_id IS NOT NULL
ON CONFLICT DO NOTHING;
