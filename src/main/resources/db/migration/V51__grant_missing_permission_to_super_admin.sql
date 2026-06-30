-- V51: Accorder au rôle "Super Admin" la dernière permission manquante.
-- Le rôle super-admin possédait 307/308 permissions ; il lui manquait
-- "manage-test-order-assignments" (gestion des affectations d'ordres d'examen).
-- Après cette migration : 308/308 — droits complets sur tout le site.
-- Idempotent : NOT EXISTS évite tout doublon (la table n'a pas de contrainte unique).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'super-admin'
  AND p.slug = 'manage-test-order-assignments'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
