-- Le code de l'application d'authentification vaut connexion.
--
-- CE QUI EXISTAIT SANS SERVIR
-- La plateforme portait déjà tout le nécessaire au TOTP — la dépendance
-- `googleauth`, un secret par utilisateur, l'URI `otpauth://` et son QR, et
-- les points d'entrée de mise en place. L'en-tête du service annonçait même
-- « Validation du code TOTP → émission des tokens définitifs ». Mais la
-- connexion ne vérifiait que le code envoyé par courriel, et le web n'a jamais
-- eu d'écran pour activer l'application. La moitié du travail dormait.
--
-- POURQUOI CETTE COLONNE
-- Un code TOTP reste valable pendant toute sa fenêtre de trente secondes, et
-- rien n'empêchait de le rejouer. Quelqu'un qui lit six chiffres par-dessus une
-- épaule, ou les retrouve dans un journal, pouvait ouvrir une seconde session
-- avant qu'ils n'expirent. On retient donc le dernier pas de temps accepté, et
-- l'on refuse tout code du même pas ou d'un pas antérieur. Le code envoyé par
-- courriel, lui, était déjà supprimé après usage.
--
-- CE QUE CELA NE CHANGE PAS
-- Le courriel reste la voie de secours, pour tout le monde et à tout moment.
-- Aucun utilisateur ne peut se retrouver enfermé dehors : téléphone perdu, on
-- demande un code par courriel ; boîte inaccessible, on lit l'application.

ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_last_step BIGINT;

COMMENT ON COLUMN users.two_factor_last_step IS
    'Dernier pas de temps TOTP accepté (secondes Unix / 30). Un code du même pas ou d''un pas antérieur est refusé, ce qui interdit le rejeu dans la fenêtre de validité.';
