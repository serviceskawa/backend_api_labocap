-- La permission d'écrire les affectations n'existait pas.
--
-- CE QUE LA PRODUCTION MONTRE
-- La table `permissions` contient `view-test-order-assignments`, mais pas
-- `manage-test-order-assignments`. Cette dernière n'a jamais été créée, ni par
-- la reprise Laravel ni depuis.
--
-- TROIS MIGRATIONS ONT DONC TOURNÉ DANS LE VIDE
-- V51 croyait l'accorder au super-admin ; V75 la reporter sur les rôles
-- détenant `edit-reports` puis sur le laborantin ; V77 la retirer aux autres.
-- Chacune joignait la table `permissions` sur un slug absent : aucune ligne, et
-- donc aucune écriture. Flyway les a toutes enregistrées en succès — un INSERT
-- qui n'insère rien n'est pas une erreur.
--
-- CE QUE CELA A CAUSÉ
-- Le contrôleur des affectations exige cette permission pour créer un lot, y
-- ajouter une demande ou en retirer une. Personne ne pouvant la détenir, ces
-- trois gestes étaient devenus impossibles pour tout le monde — sur le mobile
-- comme sur le web, super-admin compris. La lecture, elle, fonctionnait :
-- `view-test-order-assignments` existe bel et bien.
--
-- CETTE MIGRATION
-- Crée la permission manquante, puis l'accorde au laborantin et au super-admin
-- — les deux métiers retenus pour composer les lots. Elle réaligne aussi la
-- permission de lecture sur ces deux rôles, pour que le droit de lire et celui
-- d'écrire aillent de pair.
--
-- POURQUOI CE DÉFAUT A ÉCHAPPÉ SI LONGTEMPS
-- Une migration qui n'accorde rien se distingue mal d'une migration qui
-- accorde. Les suivantes s'y sont fiées, et l'erreur s'est propagée sans jamais
-- se signaler. C'est l'argument le plus net en faveur d'un contrôle qui
-- vérifie, après coup, que le droit visé existe bien.

-- 1. Créer la permission absente.
INSERT INTO permissions (id, name, slug, created_at, updated_at)
SELECT gen_random_uuid(), v.name, v.slug, NOW(), NOW()
FROM (VALUES
        ('manage test order assignments', 'manage-test-order-assignments')
     ) AS v(name, slug)
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.slug = v.slug
);

-- 2. L'accorder — avec la lecture — aux deux métiers qui composent les lots.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug IN ('laborantin', 'super-admin')
  AND p.slug IN ('view-test-order-assignments', 'manage-test-order-assignments')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = p.id
  );

-- 3. Garde-fou : si la permission n'a pas été créée, cette migration a échoué
--    en silence comme les précédentes. On préfère un échec bruyant.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM permissions WHERE slug = 'manage-test-order-assignments'
    ) THEN
        RAISE EXCEPTION 'La permission manage-test-order-assignments est toujours absente.';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions rp
        JOIN roles r ON r.id = rp.role_id
        JOIN permissions p ON p.id = rp.permission_id
        WHERE r.slug = 'laborantin' AND p.slug = 'manage-test-order-assignments'
    ) THEN
        RAISE EXCEPTION 'Le rôle laborantin n''a pas reçu manage-test-order-assignments.';
    END IF;
END $$;
