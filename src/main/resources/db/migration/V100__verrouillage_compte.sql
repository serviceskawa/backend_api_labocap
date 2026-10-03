-- Verrouillage d'un compte après des codes de connexion faux répétés (lot 2).
--
-- Le code à usage unique a six chiffres : sans frein par compte, il se trouve
-- par force brute depuis plusieurs adresses. Le compteur se remet à zéro au
-- succès ; la fenêtre d'une heure part du premier échec.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS otp_failed_attempts SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS otp_failures_since TIMESTAMP,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
