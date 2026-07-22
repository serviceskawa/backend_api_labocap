-- Alignement du pivot branch_user sur le schéma Laravel (table branch_user).
--
-- Laravel : id, user_id, branch_id, is_default (défaut true), timestamps, deleted_at.
-- Le middleware BranchRequired de Laravel n'accorde l'accès à une branche que si
-- la ligne pivot a is_default = 1 ET deleted_at IS NULL. On reproduit ces colonnes
-- pour porter la même logique (validation par requête via l'en-tête X-Branch-Id).
--
-- Migration purement additive (base déjà en prod, données migrées depuis Laravel) :
-- aucune donnée n'est supprimée. La PK composite (user_id, branch_id) devient une
-- contrainte d'unicité et l'identité de ligne passe sur un UUID (convention DB Java).

-- 1. Nouvelles colonnes (analogues Laravel).
ALTER TABLE branch_user ADD COLUMN IF NOT EXISTS id         UUID;
ALTER TABLE branch_user ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE branch_user ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE branch_user ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE branch_user ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

-- 2. Backfill de l'identité et des horodatages sur les lignes existantes.
UPDATE branch_user SET id         = gen_random_uuid() WHERE id IS NULL;
UPDATE branch_user SET created_at = NOW()             WHERE created_at IS NULL;
UPDATE branch_user SET updated_at = NOW()             WHERE updated_at IS NULL;

-- 3. Bascule de la clé primaire : (user_id, branch_id) -> id.
ALTER TABLE branch_user ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE branch_user ALTER COLUMN id SET NOT NULL;
ALTER TABLE branch_user DROP CONSTRAINT IF EXISTS branch_user_pkey;
ALTER TABLE branch_user ADD  CONSTRAINT branch_user_pkey PRIMARY KEY (id);

-- 4. On conserve l'unicité métier d'une affectation (une seule ligne active
--    par couple user/branche). Index unique partiel sur les lignes non supprimées.
CREATE UNIQUE INDEX IF NOT EXISTS uq_branch_user_user_branch_active
    ON branch_user (user_id, branch_id)
    WHERE deleted_at IS NULL;
