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
