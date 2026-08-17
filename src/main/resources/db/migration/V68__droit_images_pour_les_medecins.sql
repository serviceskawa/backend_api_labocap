-- Accorde aux médecins le droit de joindre des images à une demande d'examen.
--
-- POURQUOI
-- L'application mobile remplace « valider et signer » par « enregistrer le
-- bon », qui joint des photographies à la demande — le même geste que la fiche
-- web d'une demande d'examen. Cette action exige `edit-test-orders`, que le
-- rôle `docteur` ne détenait pas : un médecin connecté à l'application se
-- serait retrouvé devant un dossier sans aucune action possible.
--
-- CE QUE CELA OUVRE, ET CE QUE CELA N'OUVRE PAS
-- `edit-test-orders` couvre la modification d'une demande, images comprises.
-- Elle ne touche NI à la validation médicale — restée sur `validate-reports`
-- depuis V66 — NI à la remise au comptoir, qui relève de `deliver-reports`.
-- Les trois gestes demeurent distincts.
--
-- Le rôle le détient déjà chez `laborantin`, `secretariat`,
-- `secretariat-assistant` et `super-admin` : cette migration aligne `docteur`
-- sur ses confrères, elle n'invente pas un droit nouveau.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.slug = 'docteur'
  AND p.slug = 'edit-test-orders'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
