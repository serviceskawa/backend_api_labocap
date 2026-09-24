-- Module Biologie clinique : permissions et réglages par défaut.
--
-- POURQUOI V98 ET NON V99
-- Le plan réservait V98 à la saisie des résultats (B5) et V99 aux permissions.
-- Mais Flyway refuse, par défaut, une migration plus ancienne que la dernière
-- appliquée (`outOfOrder` est à false, et `ignoreMigrationPatterns` ne tolère que
-- les migrations « futures ») : une V98 livrée après une V99 déjà passée en
-- production ferait échouer la validation au démarrage. Les numéros suivent donc
-- l'ordre de livraison — permissions en V98, résultats en V99.
--
-- 1. PERMISSIONS
-- Sept droits propres à la biologie. La lecture du catalogue réutilise
-- `view-tests` ; les bons réutilisent les droits des demandes d'examen.
--
-- À QUI ELLES VONT
-- On reporte d'abord chaque droit sur les rôles qui détiennent déjà son
-- équivalent en anatomie pathologique, comme V75 : personne ne découvre un droit
-- sans rapport avec son métier, et un laboratoire qui a réorganisé ses rôles
-- retrouve la même répartition.
--   * catalogue (paramètres, antibiotiques, options de culture) ← `edit-tests`
--   * lecture des résultats                                   ← `view-reports`
--   * validation biologique du compte-rendu                   ← `validate-reports`
--     (restreinte par V66 au docteur et au super-admin)
-- Puis on accorde explicitement :
--   * au super-admin (et à l'admin historique de V2) : tout ;
--   * au laborantin : lire, saisir et valider techniquement les résultats —
--     mais pas la validation biologique, qui engage la signature du biologiste.
--
-- GARDE-FOU
-- V78 raconte comment trois migrations d'attribution ont « réussi » sans rien
-- accorder. Celle-ci se termine donc par une vérification qui échoue bruyamment
-- si un droit n'existe pas ou n'a été accordé à personne.
--
-- 2. RÉGLAGES
-- Les choix propres à chaque laboratoire ne sont pas codés en dur : ils vivent
-- dans `setting_apps`, par succursale, éditables depuis l'écran Paramètres. On
-- ne crée que les clés absentes — une valeur déjà réglée n'est jamais écrasée.
-- Ils sont placés ici plutôt que dans V97 : V97 ne porte que du schéma, et les
-- données d'amorçage (droits, réglages) se rejouent au même endroit.

-- ---------------------------------------------------------------------------
-- 1.a Créer les permissions absentes.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (id, name, slug, created_at, updated_at)
SELECT gen_random_uuid(), v.name, v.slug, NOW(), NOW()
FROM (VALUES
        ('Gérer les paramètres de biologie',         'manage-biology-parameters'),
        ('Gérer les antibiotiques',                  'manage-antibiotics'),
        ('Gérer les options de culture',             'manage-culture-options'),
        ('Voir les résultats de biologie',           'view-biology-results'),
        ('Saisir les résultats de biologie',         'edit-biology-results'),
        ('Valider techniquement les résultats',      'validate-biology-results'),
        ('Valider les comptes-rendus de biologie',   'validate-biology-reports')
     ) AS v(name, slug)
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.slug = v.slug
);

-- ---------------------------------------------------------------------------
-- 1.b Report depuis les droits équivalents d'anatomie pathologique.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, cible.id
FROM role_permissions rp
JOIN permissions source ON source.id = rp.permission_id
JOIN permissions cible ON cible.slug IN (
        CASE WHEN source.slug = 'edit-tests'       THEN 'manage-biology-parameters' END,
        CASE WHEN source.slug = 'edit-tests'       THEN 'manage-antibiotics' END,
        CASE WHEN source.slug = 'edit-tests'       THEN 'manage-culture-options' END,
        CASE WHEN source.slug = 'view-reports'     THEN 'view-biology-results' END,
        CASE WHEN source.slug = 'validate-reports' THEN 'validate-biology-reports' END
    )
WHERE source.slug IN ('edit-tests', 'view-reports', 'validate-reports')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = rp.role_id AND deja.permission_id = cible.id
  );

-- ---------------------------------------------------------------------------
-- 1.c Super-admin (et admin historique) : tous les droits de biologie.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug IN ('super-admin', 'admin')
  AND p.slug IN ('manage-biology-parameters', 'manage-antibiotics', 'manage-culture-options',
                 'view-biology-results', 'edit-biology-results',
                 'validate-biology-results', 'validate-biology-reports')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = p.id
  );

-- ---------------------------------------------------------------------------
-- 1.d Laborantin : la paillasse — lire, saisir, valider techniquement.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'laborantin'
  AND p.slug IN ('view-biology-results', 'edit-biology-results', 'validate-biology-results')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = p.id
  );

-- ---------------------------------------------------------------------------
-- 2. Réglages par défaut, pour chaque succursale qui ne les a pas encore.
-- ---------------------------------------------------------------------------
INSERT INTO setting_apps (id, branch_id, key, value, label, created_at, updated_at)
SELECT gen_random_uuid(), b.id, v.key, v.value, v.label, NOW(), NOW()
FROM branches b
CROSS JOIN (VALUES
        ('bio_antibiogram_labels',
         '{"S":"Sensible","I":"Intermédiaire","R":"Résistant"}',
         'Biologie — libellés de l''antibiogramme (la valeur enregistrée reste S/I/R)'),
        ('bio_validation_mode',
         'TWO_STEP',
         'Biologie — validation : TWO_STEP (technicien puis biologiste) ou ONE_STEP (biologiste seul)'),
        ('bio_dashboard_mode',
         'SEPARATE',
         'Biologie — tableau de bord : SEPARATE ou COMBINED avec l''anatomie pathologique'),
        ('bio_flag_labels',
         '{"L":"Bas","H":"Haut","LL":"Critique bas","HH":"Critique haut"}',
         'Biologie — libellés des indicateurs hors normes'),
        ('bio_print_provisional',
         'true',
         'Biologie — imprimer un compte-rendu non validé avec la mention « RÉSULTATS PROVISOIRES »')
     ) AS v(key, value, label)
WHERE NOT EXISTS (
    SELECT 1 FROM setting_apps s
    WHERE s.key = v.key
      AND s.branch_id = b.id
      AND s.deleted_at IS NULL
);

-- ---------------------------------------------------------------------------
-- 3. Garde-fou : un droit absent ou accordé à personne est une migration
--    qui a échoué en silence. On préfère un échec bruyant.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    s TEXT;
BEGIN
    FOREACH s IN ARRAY ARRAY['manage-biology-parameters', 'manage-antibiotics', 'manage-culture-options',
                             'view-biology-results', 'edit-biology-results',
                             'validate-biology-results', 'validate-biology-reports'] LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE slug = s) THEN
            RAISE EXCEPTION 'La permission % est absente.', s;
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM role_permissions rp
            JOIN permissions p ON p.id = rp.permission_id
            WHERE p.slug = s
        ) THEN
            RAISE EXCEPTION 'La permission % n''a été accordée à aucun rôle.', s;
        END IF;
        IF EXISTS (SELECT 1 FROM roles WHERE slug = 'super-admin') AND NOT EXISTS (
            SELECT 1
            FROM role_permissions rp
            JOIN roles r ON r.id = rp.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE r.slug = 'super-admin' AND p.slug = s
        ) THEN
            RAISE EXCEPTION 'Le rôle super-admin n''a pas reçu %.', s;
        END IF;
    END LOOP;
END $$;
