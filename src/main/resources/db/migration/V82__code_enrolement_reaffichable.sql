-- Le code d'enrôlement devient réaffichable.
--
-- La base n'en gardait que l'empreinte BCrypt : le QR n'existait qu'à l'instant
-- de sa création, et refermer l'écran coûtait un accès à rouvrir. On conserve
-- donc le code lui-même — chiffré (AES-256-GCM, clé applicative), jamais en
-- clair : une copie de la base ne suffit pas à le lire, il faut aussi la
-- configuration du serveur.
--
-- L'empreinte reste la référence pour valider un enrôlement. Cette colonne ne
-- sert qu'à remontrer le QR, et vaut nul quand aucune clé n'est configurée ou
-- pour les codes délivrés avant ce changement : ils enrôlent encore, mais ne
-- se réaffichent pas.
--
-- Le PIN, lui, reste en BCrypt et demeure irrécupérable — c'est la clé de
-- session, celle qu'il ne faut pas pouvoir relire.

ALTER TABLE mobile_enrollment_codes
    ADD COLUMN IF NOT EXISTS code_chiffre VARCHAR(512);
