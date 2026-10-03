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

-- Purge de rétention (V103) : les tests ne jouent pas Flyway.
CREATE OR REPLACE FUNCTION purger_journaux(avant TIMESTAMP)
RETURNS TABLE(acces BIGINT, actions BIGINT)
LANGUAGE plpgsql SECURITY DEFINER AS $fn$
DECLARE a BIGINT; b BIGINT;
BEGIN
    DELETE FROM journal_acces WHERE at < avant;
    GET DIAGNOSTICS a = ROW_COUNT;
    DELETE FROM log_reports WHERE created_at < avant;
    GET DIAGNOSTICS b = ROW_COUNT;
    RETURN QUERY SELECT a, b;
END $fn$;
