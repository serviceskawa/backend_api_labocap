-- Les rôles qui portent l'organisation du laboratoire ne se suppriment pas.
--
-- CE QUI POUVAIT ARRIVER
-- La suppression n'était refusée que si le rôle était encore attribué à
-- quelqu'un. Un laboratoire qui réorganise ses comptes peut se retrouver un
-- instant sans aucun laborantin — et à cet instant précis, rien n'empêchait
-- d'effacer le rôle. Les permissions qu'il portait disparaissaient avec lui, et
-- les recréer à l'identique suppose de se souvenir d'une trentaine de cases.
--
-- POURQUOI UN INDICATEUR ET NON UNE LISTE DANS LE CODE
-- Trois raisons. Un rôle renommé — « Docteur » devenu « Pathologiste » — garde
-- sa protection, là où une liste de noms la perdrait en silence. Le web peut
-- masquer le bouton au lieu de le laisser mener à un refus. Et un laboratoire
-- qui juge un autre rôle tout aussi structurant peut le protéger sans qu'on
-- publie une version.
--
-- CE QUE CELA NE FAIT PAS
-- La protection ne s'applique qu'à la suppression. Ces rôles restent
-- modifiables : on peut les renommer et changer leurs permissions, car c'est le
-- travail normal d'un administrateur.

ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_protected BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE roles
SET is_protected = TRUE
WHERE slug IN ('laborantin', 'secretariat', 'docteur');

COMMENT ON COLUMN roles.is_protected IS
    'Rôle structurant : sa suppression est refusée par le serveur. Modification et renommage restent permis.';
