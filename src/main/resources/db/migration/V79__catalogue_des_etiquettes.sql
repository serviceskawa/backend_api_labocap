-- Les étiquettes de prélèvement déjà employées, pour les reproposer.
--
-- CE QUI MANQUAIT
-- V74 enregistre les étiquettes sur la ligne d'affectation où elles ont été
-- saisies. Elles y sont donc conservées, mais nulle part rassemblées : une
-- étiquette créée à la main sur un lot n'était plus proposée sur le suivant, et
-- le technicien la ressaisissait — ou en saisissait une variante, « l1 » pour
-- « L1 », que rien ne rapprochait de la première.
--
-- CE QUE CETTE TABLE APPORTE
-- Le vocabulaire réellement employé par le laboratoire, alimenté par l'usage
-- plutôt que décidé d'avance. L'application propose ce catalogue au lieu d'une
-- liste figée dans le code, qui ne pouvait convenir à tous les laboratoires.
--
-- POURQUOI UNE TABLE PLUTÔT QU'UNE LECTURE DES LIGNES
-- On aurait pu recomposer le catalogue en parcourant les `labels` de toutes les
-- lignes. Cela suppose de lire du JSON en masse à chaque ouverture d'écran, et
-- de le refaire à mesure que les affectations s'accumulent. Une table répond en
-- une requête et ne grossit qu'avec le vocabulaire, non avec les dossiers.
--
-- CLOISONNEMENT
-- Par branche, comme le reste : deux sites d'un même laboratoire n'étiquettent
-- pas forcément de la même façon, et rien ne justifie de leur imposer le
-- vocabulaire de l'autre.

CREATE TABLE IF NOT EXISTS sample_labels (
    id          UUID PRIMARY KEY,
    branch_id   UUID         NOT NULL,
    value       VARCHAR(40)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  UUID,
    updated_by  UUID
);

-- Une étiquette une seule fois par branche, quelle que soit la casse employée :
-- « L1 » et « l1 » désignent le même contenant, et les distinguer encombrerait
-- la liste de doublons visuels.
CREATE UNIQUE INDEX IF NOT EXISTS idx_sample_labels_unique
    ON sample_labels (branch_id, UPPER(value));

COMMENT ON TABLE sample_labels IS
    'Vocabulaire des étiquettes de prélèvement, alimenté par les affectations et proposé à la saisie.';

-- Reprise de l'existant : les étiquettes déjà saisies rejoignent le catalogue,
-- pour que celui-ci ne s'ouvre pas vide sur une base en service.
INSERT INTO sample_labels (id, branch_id, value, created_at, updated_at)
SELECT DISTINCT ON (d.branch_id, UPPER(e.valeur))
       gen_random_uuid(), d.branch_id, e.valeur, NOW(), NOW()
FROM test_order_assignment_details d
CROSS JOIN LATERAL json_array_elements_text(d.labels::json) AS e(valeur)
WHERE d.labels IS NOT NULL
  AND d.labels <> ''
  AND TRIM(e.valeur) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM sample_labels s
      WHERE s.branch_id = d.branch_id AND UPPER(s.value) = UPPER(e.valeur)
  );
