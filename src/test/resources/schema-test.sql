-- Complément du schéma que Hibernate crée depuis les entités, pour les tests
-- d'intégration (joué par spring.sql.init, après Hibernate).

-- Les requêtes de recherche appellent unaccent().
CREATE EXTENSION IF NOT EXISTS unaccent;

-- branch_user n'est pour JPA qu'une table de jointure (user_id, branch_id) ;
-- la requête native hasBranchAccess lit aussi is_default et deleted_at, venus
-- du pivot Laravel.
ALTER TABLE branch_user
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Purge de rétention (V107) : les tests ne jouent pas Flyway. En SQL pur, sans
-- point-virgule interne : spring.sql.init découpe le fichier sur « ; ».
CREATE OR REPLACE FUNCTION purger_journaux(avant TIMESTAMP)
RETURNS TABLE(acces BIGINT, actions BIGINT)
LANGUAGE sql SECURITY DEFINER AS $fn$
    WITH a AS (DELETE FROM journal_acces WHERE at < avant RETURNING 1),
         b AS (DELETE FROM log_reports WHERE created_at < avant RETURNING 1)
    SELECT (SELECT count(*) FROM a), (SELECT count(*) FROM b)
$fn$;
