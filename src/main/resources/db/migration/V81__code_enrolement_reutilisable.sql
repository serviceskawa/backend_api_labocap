-- Le code d'enrôlement devient réutilisable jusqu'à sa révocation.
--
-- Il s'éteignait au premier appareil enrôlé : un agent qui changeait de
-- téléphone, ou dont l'installation échouait, devait faire rouvrir son accès.
-- Rien ne l'imposait — la révocation existait déjà, et c'est elle qui doit
-- décider de la fin d'un code, pas le hasard d'un premier usage.
--
-- La validité ne tient donc plus au temps. `expires_at` reste renseignée pour
-- les codes déjà délivrés, qui gardent leur échéance : les ignorer ranimerait
-- des codes périmés.

ALTER TABLE mobile_enrollment_codes
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS revoked_by uuid,
    ADD COLUMN IF NOT EXISTS used_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE mobile_enrollment_codes
    ALTER COLUMN expires_at DROP NOT NULL;

-- Un code déjà employé l'a été une fois : le compteur part de là plutôt que de
-- zéro, qui laisserait croire à un code jamais servi.
UPDATE mobile_enrollment_codes SET used_count = 1 WHERE used_at IS NOT NULL;
