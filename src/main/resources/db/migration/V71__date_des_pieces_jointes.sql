-- Quand chaque pièce jointe d'une demande a été ajoutée.
--
-- POURQUOI CETTE COLONNE
-- Le suivi d'un dossier se lit comme une chronologie : demande enregistrée,
-- pièces jointes, retrait. Les deux jalons extrêmes sont datés ; celui du
-- milieu ne l'était pas. Les images sont stockées sous un nom aléatoire
-- (`UUID.uuid`) dans une liste JSON de noms : ni le fichier ni la base ne
-- portaient la moindre date.
--
-- POURQUOI UNE LISTE PARALLÈLE ET NON UNE TABLE
-- Une table d'images serait plus juste, mais elle obligerait à réécrire l'envoi,
-- la suppression et la lecture, côté web comme côté mobile, et à inventer une
-- date pour toutes les images déjà là. Cette colonne s'ajoute sans rien casser :
-- elle suit `files_name` index par index, et reste vide pour l'existant.
--
-- CE QU'ELLE N'APPORTE PAS
-- Les pièces jointes antérieures resteront sans date. On ne l'affichera pas
-- plutôt que d'en inventer une — la date de la demande n'est pas celle du bon
-- photographié trois jours plus tard.

ALTER TABLE test_orders ADD COLUMN IF NOT EXISTS files_added_at TEXT;

COMMENT ON COLUMN test_orders.files_added_at IS
    'Dates d''ajout des pièces jointes, en JSON, alignées index par index sur files_name. Vide pour les images antérieures à cette colonne.';
