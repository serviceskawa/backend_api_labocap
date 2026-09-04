-- Le jeton de notification d'un appareil enrôlé.
--
-- Porté par l'appareil et non par la personne : un médecin peut avoir deux
-- téléphones, et un téléphone changer de main. Révoquer l'appareil éteint donc
-- ses notifications du même geste, sans qu'on ait à y penser.
--
-- Le jeton change tout seul — réinstallation, effacement des données, décision
-- du système. `push_token_at` dit de quand date celui qu'on garde : un jeton
-- ancien qui échoue à l'envoi se distingue ainsi d'un jeton jamais reçu.

ALTER TABLE mobile_devices
    ADD COLUMN IF NOT EXISTS push_token VARCHAR(512),
    ADD COLUMN IF NOT EXISTS push_token_at TIMESTAMP;

-- L'envoi part des participants d'un fil et cherche leurs appareils vivants.
CREATE INDEX IF NOT EXISTS idx_mobile_devices_push
    ON mobile_devices (user_id)
    WHERE push_token IS NOT NULL AND revoked_at IS NULL;
