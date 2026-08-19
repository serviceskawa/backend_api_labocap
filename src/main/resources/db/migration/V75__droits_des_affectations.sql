-- Organiser les affectations ne devrait pas exiger le droit de modifier un
-- compte rendu.
--
-- CE QUI CLOCHE AUJOURD'HUI
-- Le contrôleur des affectations est gardé par `view-reports` et `edit-reports`.
-- Or ces permissions donnent bien davantage : corriger le contenu médical d'un
-- compte rendu. Un technicien qui ne fait que grouper des demandes pour un
-- docteur devait donc détenir le droit d'en réécrire le diagnostic.
--
-- Les bonnes permissions existent pourtant déjà en base — `view-test-order-
-- assignments` et `manage-test-order-assignments` — et l'une des deux est même
-- employée ailleurs, dans `TestOrderController`. Le contrôleur des affectations
-- ne les a simplement jamais utilisées.
--
-- CE QUE FAIT CETTE MIGRATION
-- 1. Elle donne les permissions d'affectation à tout rôle qui détient
--    aujourd'hui les permissions de compte rendu correspondantes. Personne ne
--    perd donc l'accès qu'il avait : le changement de garde côté Java est
--    neutre pour l'existant.
-- 2. Elle les donne au laborantin, qui en a besoin pour le parcours mobile —
--    c'est le seul élargissement, et il est délibéré.
--
-- Le laborantin ne gagne rien d'autre : ni lecture ni écriture des comptes
-- rendus eux-mêmes.

-- 1. Report des droits existants, pour ne casser aucun accès en place.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, cible.id
FROM role_permissions rp
JOIN permissions source ON source.id = rp.permission_id
JOIN permissions cible ON cible.slug = CASE source.slug
        WHEN 'view-reports' THEN 'view-test-order-assignments'
        WHEN 'edit-reports' THEN 'manage-test-order-assignments'
    END
WHERE source.slug IN ('view-reports', 'edit-reports')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = rp.role_id AND deja.permission_id = cible.id
  );

-- 2. Le laborantin, qui constitue les affectations depuis l'application.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'laborantin'
  AND p.slug IN ('view-test-order-assignments', 'manage-test-order-assignments')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = p.id
  );
