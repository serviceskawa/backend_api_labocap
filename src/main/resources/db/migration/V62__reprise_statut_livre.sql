-- V62 — Reprise du statut « livré » sur l'historique migré de Laravel
--
-- Trois colonnes disent qu'un compte rendu a été remis, et une seule était
-- renseignée sur les données reprises :
--
--     test_orders.status  = 'DELIVERED'        0 ligne
--     reports.status      = 'DELIVERED'        0 ligne
--     reports.is_delivered = true          6 956 lignes
--
-- Le code Java pose bien les trois ensemble (TestOrderServiceImpl.markAsDelivered
-- et ReportServiceImpl.markDelivered), mais ces remises datent de Laravel : la
-- synchronisation a été écrite après, et l'historique n'a jamais été repris.
--
-- Conséquence visible : la liste des demandes affichait « Livrer : 0 » alors que
-- la pastille de chaque ligne annonçait « Livré ». Le compteur interrogeait le
-- statut du bon, la pastille le drapeau du rapport, et les deux avaient raison.
--
-- On aligne donc les deux statuts sur le drapeau, qui fait foi : c'est lui que
-- Laravel écrivait, et lui seul porte l'information.

-- 1. Le compte rendu remis prend le statut correspondant.
--    Restreint aux VALIDATED : un brouillon marqué remis serait une anomalie
--    qu'il ne faut pas entériner en le déclarant livré.
UPDATE reports
   SET status = 'DELIVERED'
 WHERE deleted_at IS NULL
   AND is_delivered = true
   AND status = 'VALIDATED';

-- 2. Le bon d'examen suit son compte rendu.
--    Restreint aux VALIDATED également : `markAsDelivered` refuse de livrer un
--    bon qui ne l'est pas, et un bon PENDING dont le rapport serait remis
--    relèverait d'une incohérence à examiner, pas à propager.
UPDATE test_orders o
   SET status = 'DELIVERED'
 WHERE o.deleted_at IS NULL
   AND o.status = 'VALIDATED'
   AND EXISTS (
         SELECT 1 FROM reports r
          WHERE r.test_order_id = o.id
            AND r.deleted_at IS NULL
            AND r.is_delivered = true
       );
