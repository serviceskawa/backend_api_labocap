-- La colonne d'effacement logique manquait à `sample_labels`.
--
-- CE QUI S'EST PASSÉ
-- V79 a créé la table en reprenant les colonnes d'audit une à une — id,
-- branch_id, created_at, updated_at, created_by, updated_by — et en oubliant
-- `deleted_at`, que `AuditableEntity` porte aussi. Flyway a créé la table sans
-- rien signaler ; c'est Hibernate qui a refusé de démarrer, sa validation de
-- schéma exigeant que l'entité et la table coïncident exactement.
--
-- Le serveur a donc redémarré en boucle après le déploiement : la migration
-- passait, la validation échouait, le conteneur repartait.
--
-- POURQUOI UNE MIGRATION DE PLUS PLUTÔT QU'UNE CORRECTION DE V79
-- V79 est appliquée en production. La retoucher changerait son empreinte, et
-- Flyway refuserait de démarrer sur une base l'ayant déjà exécutée — on
-- remplacerait une panne par une autre. Une migration ne se réécrit pas une
-- fois partie.

ALTER TABLE sample_labels ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
