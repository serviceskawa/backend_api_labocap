-- Les étiquettes physiques des prélèvements d'une demande affectée.
--
-- CE QUE C'EST
-- Une demande d'examen peut regrouper plusieurs prélèvements, chacun portant
-- une étiquette collée sur son contenant : L1, L2, L3… Le technicien qui
-- constitue une affectation pour un docteur note lesquels partent, car ils ne
-- partent pas toujours tous en même temps. Sans cette information, une
-- affectation dit « la demande 26-0188 » là où la paillasse manipule « L1 et L2
-- de la demande 26-0188 » — et c'est la seconde formulation qui permet de
-- retrouver un tube.
--
-- POURQUOI UNE COLONNE JSON ET NON UNE TABLE
-- Les étiquettes ne sont jamais lues seules : elles accompagnent toujours la
-- ligne d'affectation, et le filtre par étiquette porte sur une affectation
-- déjà chargée. Une table imposerait une jointure à chaque lecture pour un
-- besoin que personne n'a. La liste suit le même usage que `files_name` sur les
-- demandes, déjà en place.
--
-- POURQUOI FACULTATIF
-- Les affectations déjà enregistrées n'en ont pas, et on ne peut pas deviner
-- après coup quels prélèvements sont partis. Elles resteront sans étiquette.

ALTER TABLE test_order_assignment_details
    ADD COLUMN IF NOT EXISTS labels TEXT;

COMMENT ON COLUMN test_order_assignment_details.labels IS
    'Étiquettes physiques des prélèvements affectés (« L1 », « L2 »…), en JSON. Vide pour les affectations antérieures.';
