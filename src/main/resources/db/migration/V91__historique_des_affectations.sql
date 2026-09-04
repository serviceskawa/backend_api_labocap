-- Une demande d'examen change parfois de médecin. Jusqu'ici, elle ne le
-- pouvait pas : la seconde affectation était refusée en silence, et la ligne
-- restait attachée au premier médecin. Personne n'était averti — l'écran
-- annonçait l'ajout, et le dossier n'avait pas bougé.
--
-- Plutôt qu'une table d'historique séparée, on laisse la demande porter
-- plusieurs lignes d'affectation et on marque celles qui ont été remplacées.
-- L'historique est alors la suite des lignes elles-mêmes : il ne peut pas
-- diverger de l'affectation réelle, ce qu'une table parallèle aurait fini par
-- faire dès la première écriture oubliée.
ALTER TABLE test_order_assignment_details
    ADD COLUMN IF NOT EXISTS remplacee_le timestamp(6);

-- Les installations dont une demande figure déjà dans deux lots — la relation
-- n'a jamais été contrainte — désignent la plus récente comme courante et
-- datent les autres de leur dernière modification, faute de mieux : nul ne sait
-- quand elles ont cessé de valoir.
WITH classees AS (
    SELECT id,
           row_number() OVER (PARTITION BY test_order_id
                              ORDER BY created_at DESC, id DESC) AS rang
    FROM test_order_assignment_details
    WHERE deleted_at IS NULL
      AND test_order_id IS NOT NULL
)
UPDATE test_order_assignment_details d
SET remplacee_le = d.updated_at
FROM classees c
WHERE c.id = d.id
  AND c.rang > 1
  AND d.remplacee_le IS NULL;

-- L'affectation courante d'une demande se lit à chaque ouverture de dossier,
-- de compte rendu et de fil de discussion. L'index partiel ne couvre que les
-- lignes vivantes, qui sont l'immense majorité des lectures et une petite part
-- des lignes une fois l'historique constitué.
CREATE INDEX IF NOT EXISTS idx_affectation_courante
    ON test_order_assignment_details (test_order_id)
    WHERE remplacee_le IS NULL AND deleted_at IS NULL;
