-- Socle d'authentification de l'application mobile de signature.
--
-- PRINCIPE
-- Rien n'est embarqué dans le binaire. Une clé d'API livrée avec l'application
-- serait extraite en quelques minutes : elle ne peut donc porter aucune
-- autorisation. À la place, chaque téléphone est enrôlé individuellement et
-- parle ensuite avec sa propre identité, révocable seule — un appareil perdu se
-- coupe sans toucher aux autres.
--
-- TROIS FACTEURS, TROIS RÔLES DISTINCTS
--   1. L'appareil enrôlé      — ce qu'on possède, et que le serveur peut révoquer.
--   2. Le code PIN            — ce qu'on sait, propre à chaque utilisateur.
--   3. La permission dédiée   — le droit d'employer l'application, accordé au cas par cas.
--
-- Le PIN n'identifie personne : l'utilisateur se désigne, le PIN reste un
-- secret. C'est ce qui rend l'imputation au journal exacte plutôt que probable.
-- Un PIN qui servirait aussi d'identifiant devrait être unique dans tout le
-- laboratoire, et quatre chiffres ne laissent que dix mille combinaisons : à
-- cent utilisateurs, un attaquant tombe sur un compte valide en une centaine
-- d'essais.

-- ---------------------------------------------------------------------------
-- Code PIN, porté par l'utilisateur
-- ---------------------------------------------------------------------------
-- Haché comme les mots de passe, avec le même encodeur. Les deux compteurs
-- servent au verrouillage temporaire : un PIN est court par nature, donc
-- attaquable par force brute si rien ne freine les tentatives.
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_hash             VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_failed_attempts  SMALLINT     NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_locked_until     TIMESTAMP;

-- ---------------------------------------------------------------------------
-- Appareils enrôlés
-- ---------------------------------------------------------------------------
-- `public_key` reçoit la clé publique produite par l'enclave sécurisée du
-- téléphone (Keychain sur iOS, Keystore sur Android). La clé privée n'en sort
-- jamais. Elle servira à signer la validation médicale : le serveur vérifie
-- alors que l'acte vient bien de cet appareil-là, ce que le web ne sait pas
-- faire aujourd'hui puisqu'une session ouverte y suffit.
--
-- La révocation est une date et non une suppression : couper un appareil ne
-- doit pas effacer la trace des actes qu'il a portés.
CREATE TABLE IF NOT EXISTS mobile_devices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id    UUID,
    label        VARCHAR(120) NOT NULL,
    public_key   TEXT         NOT NULL,
    enrolled_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP,
    revoked_at   TIMESTAMP,
    revoked_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mobile_devices_user   ON mobile_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_mobile_devices_actifs ON mobile_devices(user_id) WHERE revoked_at IS NULL;

-- ---------------------------------------------------------------------------
-- Codes d'enrôlement, à usage unique
-- ---------------------------------------------------------------------------
-- Seul point d'entrée ouvert de toute la chaîne. Un administrateur délivre le
-- code à une personne nommée ; l'application l'échange contre les identifiants
-- de l'appareil.
--
-- Le code est haché, donc introuvable par recherche directe : l'enrôlement
-- demande aussi l'adresse de l'utilisateur, on retrouve ses codes vivants puis
-- on les vérifie un à un. C'est voulu — l'utilisateur se désigne, ici comme à
-- la connexion.
--
-- `used_at` plutôt qu'une suppression : on veut pouvoir dire quel code a servi
-- à enrôler quel appareil, et quand.
CREATE TABLE IF NOT EXISTS mobile_enrollment_codes (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash  VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    device_id  UUID      REFERENCES mobile_devices(id) ON DELETE SET NULL,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_enrollment_codes_vivants
    ON mobile_enrollment_codes(user_id) WHERE used_at IS NULL;

-- ---------------------------------------------------------------------------
-- Droit d'employer l'application
-- ---------------------------------------------------------------------------
-- Accordée à aucun rôle par cette migration, délibérément : le client demande
-- que l'accès soit attribué utilisateur par utilisateur. L'ouvrir d'office
-- contredirait cette exigence. Elle s'attribue dans l'écran des rôles.
--
-- Convention de nommage reprise de la base Laravel : libellé en minuscules
-- séparé par des espaces (cf. « edit reports » / « edit-reports »).
INSERT INTO permissions (id, name, slug, created_at)
SELECT gen_random_uuid(), 'use mobile app', 'use-mobile-app', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM permissions p WHERE p.slug = 'use-mobile-app'
);
