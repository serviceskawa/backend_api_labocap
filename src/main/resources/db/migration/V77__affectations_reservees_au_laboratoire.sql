-- Les affectations reviennent au laborantin et au super-admin.
--
-- CE QUE V75 AVAIT FAIT, ET POURQUOI
-- Le contrôleur des affectations exigeait `edit-reports`. En le faisant passer
-- aux permissions d'affectation, V75 a reporté celles-ci sur tout rôle détenant
-- déjà `edit-reports`, afin que personne ne perde l'accès qu'il avait au moment
-- du changement de garde. Ce report était prudent, mais il a distribué les
-- affectations bien au-delà de ceux qui en ont l'usage — le secrétariat, entre
-- autres, s'est retrouvé avec un écran qui ne relève pas de son métier.
--
-- CE QUE CELLE-CI FAIT
-- Elle resserre sur les deux rôles qui composent réellement des lots. Le
-- secrétariat et le docteur conservent tout le reste : le comptoir, la remise,
-- la validation. Ils perdent l'écran des affectations, sur le mobile comme sur
-- le web.
--
-- POURQUOI PAR LES PERMISSIONS ET NON PAR L'ÉCRAN
-- Masquer une carte dans l'application n'aurait rien protégé : le droit serait
-- resté accordé, et l'étanchéité des deux parcours n'aurait été qu'apparente.
-- C'est le serveur qui décide qui peut affecter.

DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND p.slug IN ('view-test-order-assignments', 'manage-test-order-assignments')
  AND r.slug NOT IN ('laborantin', 'super-admin');
