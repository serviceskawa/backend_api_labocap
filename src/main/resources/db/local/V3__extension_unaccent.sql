-- Pendant local de db/migration/V63 : le profil `local` remplace entièrement
-- `db/migration`, l'extension doit donc y être déclarée aussi, sans quoi les
-- recherches insensibles aux accents échoueraient au démarrage en développement.
CREATE EXTENSION IF NOT EXISTS unaccent;
