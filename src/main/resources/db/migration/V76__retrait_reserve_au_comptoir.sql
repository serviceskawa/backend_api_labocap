-- Remettre un résultat n'est pas le métier du laborantin.
--
-- CE QUI S'EST PASSÉ
-- V64 a créé `deliver-reports` et l'a accordée à tout rôle qui détenait déjà
-- `edit-reports` — le report le plus sûr à l'époque, puisqu'il ne retirait rien
-- à personne. V66 a ensuite resserré `validate-reports` sur le docteur et le
-- super-admin, mais n'a pas touché à `deliver-reports`. Le laborantin l'a donc
-- conservée par héritage, sans que ce soit une décision.
--
-- POURQUOI LA RETIRER
-- La remise d'un compte rendu est un acte de comptoir : on identifie la
-- personne qui emporte une donnée de santé, on recueille sa signature, on
-- documente à quel titre elle repart. Le laborantin travaille en amont, sur les
-- prélèvements. Lui laisser ce droit ne l'aide pas et brouille la trace : le
-- journal désignerait comme auteur d'une remise quelqu'un qui n'était pas au
-- guichet.
--
-- CE QUE CELA NE TOUCHE PAS
-- Ni le secrétariat, dont c'est le métier, ni le docteur, ni le super-admin.
-- Le laborantin garde tout le reste, y compris les affectations que la V75 lui
-- a ouvertes.

DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.slug = 'laborantin'
  AND p.slug = 'deliver-reports';
