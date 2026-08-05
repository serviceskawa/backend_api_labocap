-- ─────────────────────────────────────────────────────────────────────────────
-- Jeu de données LOCAL — de quoi se connecter et travailler sur base vierge.
--
-- ⚠ RÉSERVÉ AU PROFIL `local` (cf. application-local.yml). Jamais appliqué en
-- production : cet emplacement Flyway (db/local) n'y est pas déclaré.
--
-- Contenu : les 39 permissions, une agence, un rôle Administrateur qui les
-- porte toutes, et un compte administrateur.
--
--   ┌─────────────────────────────────────────┐
--   │  admin@labo.local  /  Admin123!         │
--   └─────────────────────────────────────────┘
--
-- Identifiants fixes (UUID en dur) pour que les scripts et jeux de données
-- ultérieurs puissent s'y référer sans requête préalable.
-- ─────────────────────────────────────────────────────────────────────────────

-- ─── Permissions ────────────────────────────────────────────────────────────
-- Repris à l'identique de db/migration/V2__seed_permissions.sql : la table
-- permissions (id, name, slug, created_at) est la même dans les deux schémas.
INSERT INTO permissions (id, name, slug, created_at) VALUES
    (gen_random_uuid(), 'Voir patients',              'view-patients',           NOW()),
    (gen_random_uuid(), 'Créer patients',             'create-patients',         NOW()),
    (gen_random_uuid(), 'Modifier patients',          'edit-patients',           NOW()),
    (gen_random_uuid(), 'Supprimer patients',         'delete-patients',         NOW()),

    (gen_random_uuid(), 'Voir bons d''examen',        'view-test-orders',        NOW()),
    (gen_random_uuid(), 'Créer bons d''examen',       'create-test-orders',      NOW()),
    (gen_random_uuid(), 'Modifier bons d''examen',    'edit-test-orders',        NOW()),
    (gen_random_uuid(), 'Supprimer bons d''examen',   'delete-test-orders',      NOW()),

    (gen_random_uuid(), 'Voir comptes-rendus',        'view-reports',            NOW()),
    (gen_random_uuid(), 'Créer comptes-rendus',       'create-reports',          NOW()),
    (gen_random_uuid(), 'Modifier comptes-rendus',    'edit-reports',            NOW()),
    (gen_random_uuid(), 'Valider comptes-rendus',     'validate-reports',        NOW()),
    (gen_random_uuid(), 'Livrer comptes-rendus',      'deliver-reports',         NOW()),

    (gen_random_uuid(), 'Voir médecins',              'view-doctors',            NOW()),
    (gen_random_uuid(), 'Gérer médecins',             'manage-doctors',          NOW()),

    (gen_random_uuid(), 'Voir hôpitaux',              'view-hospitals',          NOW()),
    (gen_random_uuid(), 'Gérer hôpitaux',             'manage-hospitals',        NOW()),

    (gen_random_uuid(), 'Voir analyses',              'view-tests',              NOW()),
    (gen_random_uuid(), 'Gérer analyses',             'manage-tests',            NOW()),

    (gen_random_uuid(), 'Voir finances',              'view-finance',            NOW()),
    (gen_random_uuid(), 'Gérer factures',             'manage-invoices',         NOW()),
    (gen_random_uuid(), 'Gérer paiements',            'manage-payments',         NOW()),
    (gen_random_uuid(), 'Gérer caisse',               'manage-cashbox',          NOW()),

    (gen_random_uuid(), 'Voir RH',                   'view-hr',                 NOW()),
    (gen_random_uuid(), 'Gérer employés',             'manage-employees',        NOW()),

    (gen_random_uuid(), 'Voir inventaire',            'view-inventory',          NOW()),
    (gen_random_uuid(), 'Gérer inventaire',           'manage-inventory',        NOW()),

    (gen_random_uuid(), 'Voir consultations',         'view-consultations',      NOW()),
    (gen_random_uuid(), 'Gérer consultations',        'manage-consultations',    NOW()),

    (gen_random_uuid(), 'Voir contrats',              'view-contracts',          NOW()),
    (gen_random_uuid(), 'Gérer contrats',             'manage-contracts',        NOW()),

    (gen_random_uuid(), 'Gérer utilisateurs',         'manage-users',            NOW()),
    (gen_random_uuid(), 'Gérer rôles',                'manage-roles',            NOW()),
    (gen_random_uuid(), 'Gérer agences',              'manage-branches',         NOW()),

    (gen_random_uuid(), 'Voir paramètres',            'view-settings',           NOW()),
    (gen_random_uuid(), 'Gérer paramètres',           'manage-settings',         NOW()),

    (gen_random_uuid(), 'Voir support',               'view-support',            NOW()),
    (gen_random_uuid(), 'Gérer support',              'manage-support',          NOW());

-- ─── Agence, rôle et compte administrateur ──────────────────────────────────
-- Agence de rattachement (users.branch_id et roles.branch_id sont NOT NULL).
INSERT INTO branches (id, name, code, location, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Siège', 'SIEGE', 'Cotonou', NOW(), NOW());

-- Rôle Administrateur.
INSERT INTO roles (id, branch_id, name, slug, description, is_assignable, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111',
        'Administrateur', 'admin', 'Accès complet — profil local', TRUE, NOW(), NOW());

-- Le rôle porte TOUTES les permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT '22222222-2222-2222-2222-222222222222', p.id FROM permissions p;

-- Compte administrateur. Mot de passe « Admin123! » haché en BCrypt (coût 10),
-- format attendu par BCryptPasswordEncoder côté Spring Security.
INSERT INTO users (
    id, branch_id, firstname, lastname, email, password, phone,
    is_active, is_connect, two_factor_enabled, email_notification,
    created_at, updated_at
) VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'Admin', 'Local', 'admin@labo.local',
    '$2a$10$Qqwqu.ig6LkGLAaOZycLpe3q1b1FLm7dAUdhBol5bifOSdBJD8ddS',
    '+22900000000',
    TRUE, FALSE, FALSE, TRUE,
    NOW(), NOW()
);

-- Rattachement du compte au rôle.
INSERT INTO user_roles (user_id, role_id)
VALUES ('33333333-3333-3333-3333-333333333333',
        '22222222-2222-2222-2222-222222222222');

-- Agences ACCESSIBLES au compte — pivot branch_user (@ManyToMany sur User),
-- distinct de users.branch_id qui n'est que l'agence de rattachement.
-- Sans cette ligne l'API renvoie `branches: []` : le compte s'authentifie mais
-- n'a accès à aucune agence, d'où « Impossible de charger vos branches. » et
-- des écrans métier inutilisables.
INSERT INTO branch_user (user_id, branch_id)
VALUES ('33333333-3333-3333-3333-333333333333',
        '11111111-1111-1111-1111-111111111111');
