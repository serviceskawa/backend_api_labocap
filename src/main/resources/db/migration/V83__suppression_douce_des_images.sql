-- Les images d'un bon d'examen ne se suppriment plus vraiment.
--
-- `deleteImage` effaçait le fichier du disque et retirait l'entrée du tableau.
-- Or ces clichés sont une pièce du dossier : ils montrent ce qui a été reçu au
-- comptoir. Une suppression par erreur — ou contestée plus tard — ne laissait
-- aucune trace, pas même le fait qu'une image ait existé.
--
-- Troisième tableau parallèle à `files_name` et `files_added_at` : une entrée
-- non nulle marque la date de retrait. L'entrée reste en place, ce qui est
-- indispensable — les points d'entrée adressent les images par leur index, et
-- retirer une case décalerait toutes les suivantes.

ALTER TABLE test_orders
    ADD COLUMN IF NOT EXISTS files_deleted_at TEXT;
