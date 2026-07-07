-- V52: Partage de document par rôle (réplique Laravel docs.role_id)
-- Un document peut être partagé avec un rôle : tous les utilisateurs de ce rôle y accèdent.
ALTER TABLE docs ADD COLUMN IF NOT EXISTS role_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_docs_role') THEN
        ALTER TABLE docs ADD CONSTRAINT fk_docs_role
            FOREIGN KEY (role_id) REFERENCES roles(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_docs_role_id ON docs(role_id);
