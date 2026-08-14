-- Resserre la validation médicale sur les rôles qui l'exercent réellement.
--
-- POURQUOI CE N'EST PAS UNE RESTAURATION
-- Le système hérité ne connaissait ni `validate-reports` ni `deliver-reports` :
-- son dump ne contient que `create-reports`, `edit-reports` et `view-reports`.
-- La validation y était donc ouverte à quiconque pouvait modifier un compte
-- rendu. La distinction introduite en V64 est nouvelle, et celle-ci la rend
-- effective. On ne rétablit rien — on sépare ce qui ne l'avait jamais été.
--
-- CE QUE DISENT LES DONNÉES
-- Sur 13 140 comptes rendus signés, 13 139 l'ont été par un compte portant le
-- rôle `docteur`, soit 99,99 %. L'unique exception est un compte sans aucun
-- rôle, manifestement un jeu d'essai. Aucun `laborantin`, `secretariat` ni
-- `secretariat-assistant` n'a jamais signé un seul compte rendu.
--
-- Le droit qu'ils détenaient était donc théorique : le leur retirer ne change
-- rien à leur travail. C'est ce qui rend ce resserrement sûr — il ne retire une
-- capacité à personne qui s'en serve.
--
-- CE QUI N'EST PAS TOUCHÉ
-- `deliver-reports` reste accordée à tous : la remise au comptoir est
-- précisément le geste que ces rôles accomplissent, et que l'application mobile
-- va porter. Seule la signature du diagnostic se referme.
--
-- SI LE LABORATOIRE VEUT REVENIR EN ARRIÈRE
-- Le droit se réaccorde depuis l'écran des rôles, sans migration.

DELETE FROM role_permissions rp
USING permissions p, roles r
WHERE rp.permission_id = p.id
  AND rp.role_id = r.id
  AND p.slug = 'validate-reports'
  AND r.slug NOT IN ('docteur', 'super-admin');
