-- Intégrité des comptes-rendus validés.
--
-- Un compte-rendu signé reste modifiable (les compléments arrivent après la
-- remise du résultat), et chaque modification est journalisée dans
-- `log_reports` avec la liste des champs touchés. Mais la trace ne dit pas ce
-- que ces champs contenaient : l'ancien diagnostic était perdu. On le garde
-- désormais, entier, dans `report_versions`, avant chaque écrasement d'un
-- compte-rendu validé ou livré.
--
-- 1. TABLE `report_versions`
-- Les textes sont copiés colonne à colonne sous le nom qu'ils ont dans
-- `reports`, plutôt qu'en JSON : une version se lit en SQL sans décodeur, et
-- les champs d'un compte-rendu d'anatomie pathologique ne bougent plus.
-- Pas d'`updated_at` ni de `deleted_at` : la table est en ajout seul.
--
-- 2. PERMISSION `view-report-history`
-- Relire une version antérieure montre un diagnostic qui a été corrigé :
-- c'est pour les administrateurs et pour ceux qui engagent leur signature
-- (tout rôle détenant `validate-reports`), pas pour tout lecteur de
-- comptes-rendus.
--
-- 3. AJOUT SEUL : révocation d'UPDATE et DELETE
-- `log_reports`, `report_versions` et, si le lot 6 est passé, `journal_acces`
-- ne doivent jamais être modifiées ni purgées par l'application. On retire
-- UPDATE et DELETE à tous les rôles qui les détiennent — y compris le rôle qui
-- joue cette migration, s'il est propriétaire.
--
-- LIMITES, À LIRE AVANT DE COMPTER SUR CETTE PROTECTION
--   * Un superutilisateur (`postgres`, l'utilisateur par défaut de
--     `application.yml`) ignore les privilèges : la révocation ne le contraint
--     pas. La vraie protection suppose un rôle applicatif distinct, non
--     superutilisateur, non propriétaire, créé par l'exploitant.
--   * Seul le propriétaire (ou un superutilisateur) peut révoquer un privilège
--     qu'il a accordé. Si cette migration est jouée par un simple rôle
--     applicatif, PostgreSQL ne révoque rien et avertit (« no privileges could
--     be revoked ») ; l'exploitant la rejoue alors à la main en propriétaire.
--   * La purge mensuelle des journaux (lot 6) supprime dans `log_reports` et
--     `journal_acces` : elle devra tourner avec un rôle qui en a le droit, ou
--     par une fonction SQL `SECURITY DEFINER` appartenant au propriétaire.

-- ---------------------------------------------------------------------------
-- 1. Table des versions.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_versions (
    id                               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id                        UUID NOT NULL REFERENCES reports(id),
    -- Croissant par compte-rendu : 1 pour le premier état écrasé.
    version                          INTEGER NOT NULL,
    title                            VARCHAR(255),
    content                          TEXT,
    content_micro                    TEXT,
    comment                          TEXT,
    comment_sup                      TEXT,
    description_supplementaire       TEXT,
    description_supplementaire_micro TEXT,
    -- Noms des signataires en clair : une version se lit des années plus tard,
    -- quand un compte peut avoir disparu.
    signataires                      TEXT,
    status                           VARCHAR(30) NOT NULL,
    saved_at                         TIMESTAMP NOT NULL DEFAULT NOW(),
    -- Auteur de la modification qui a provoqué la prise de version. Nul si le
    -- compte a été supprimé depuis : on garde la version, pas son auteur.
    saved_by                         UUID NULL REFERENCES users(id),
    CONSTRAINT uk_report_versions_numero UNIQUE (report_id, version)
);

CREATE INDEX IF NOT EXISTS idx_report_versions_report ON report_versions(report_id, version);

-- ---------------------------------------------------------------------------
-- 2. Permission `view-report-history`.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (id, name, slug, created_at, updated_at)
SELECT gen_random_uuid(), 'Consulter les versions d''un compte-rendu', 'view-report-history', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE slug = 'view-report-history');

-- Aux administrateurs et à tout rôle qui valide des comptes-rendus.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT r.id, cible.id
FROM roles r
JOIN permissions cible ON cible.slug = 'view-report-history'
WHERE (r.slug IN ('super-admin', 'admin')
       OR EXISTS (
           SELECT 1
           FROM role_permissions rp
           JOIN permissions p ON p.id = rp.permission_id
           WHERE rp.role_id = r.id AND p.slug = 'validate-reports'))
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = cible.id
  );

-- Garde-fou (voir V78, V98) : un droit accordé à personne est une migration
-- qui a échoué en silence.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        WHERE p.slug = 'view-report-history'
    ) THEN
        RAISE EXCEPTION 'La permission view-report-history n''a été accordée à aucun rôle.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 3. Ajout seul. Le bloc est délimité : VersionsDeCompteRenduIT le rejoue tel
--    quel sur un rôle de test, ne déplacez pas les marqueurs.
-- ---------------------------------------------------------------------------
-- >>> revocation
DO $$
DECLARE
    t TEXT;
    g RECORD;
BEGIN
    FOREACH t IN ARRAY ARRAY['log_reports', 'report_versions', 'journal_acces'] LOOP
        -- `journal_acces` arrive avec le lot 6 (V101) ; la révocation ne doit
        -- pas dépendre de l'ordre de livraison.
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = current_schema() AND table_name = t
        ) THEN
            CONTINUE;
        END IF;
        -- Le catalogue plutôt qu'information_schema : cette vue ne montre que
        -- les privilèges où le rôle courant est partie, et manquerait ceux que
        -- le propriétaire a accordés à un rôle applicatif. Le propriétaire,
        -- lui, garde ses droits : c'est sous son identité que la purge de
        -- rétention s'exécute (purger_journaux, SECURITY DEFINER), et
        -- l'application ne doit pas se connecter avec lui.
        FOR g IN
            SELECT DISTINCT CASE WHEN a.grantee = 0 THEN 'PUBLIC'
                                 ELSE quote_ident(pg_get_userbyid(a.grantee)) END AS qui
            FROM pg_class c
            CROSS JOIN LATERAL aclexplode(c.relacl) a
            WHERE c.oid = t::regclass
              AND a.privilege_type IN ('UPDATE', 'DELETE')
              AND a.grantee <> c.relowner
        LOOP
            EXECUTE format('REVOKE UPDATE, DELETE ON %I FROM %s', t, g.qui);
        END LOOP;
    END LOOP;

    IF (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE NOTICE 'V103 : % est superutilisateur, la révocation d''UPDATE/DELETE ne le contraint pas — prévoir un rôle applicatif distinct.',
            current_user;
    END IF;
END $$;
-- <<< revocation

-- La purge mensuelle (PurgeDesJournaux, lot 6) est la seule suppression
-- légitime dans ces tables : elle passe par cette fonction, exécutée avec les
-- droits de son propriétaire (SECURITY DEFINER), que le rôle applicatif n'a plus.
CREATE OR REPLACE FUNCTION purger_journaux(avant TIMESTAMP)
RETURNS TABLE(acces BIGINT, actions BIGINT)
LANGUAGE sql SECURITY DEFINER AS $fn$
    WITH a AS (DELETE FROM journal_acces WHERE at < avant RETURNING 1),
         b AS (DELETE FROM log_reports WHERE created_at < avant RETURNING 1)
    SELECT (SELECT count(*) FROM a), (SELECT count(*) FROM b)
$fn$;
