-- Signature d'appareil attachée à la validation médicale.
--
-- CE QUE CELA APPORTE
-- Aujourd'hui, valider un compte-rendu ne demande qu'une session ouverte :
-- quiconque se trouve devant un écran déverrouillé peut engager un diagnostic,
-- et rien dans la trace ne distingue le pathologiste de son voisin. C'est la
-- faiblesse que le mobile permet de corriger.
--
-- L'appareil enrôlé signe un condensé de l'acte avec une clé privée qui ne
-- quitte jamais son enclave sécurisée, déverrouillée par biométrie. Le serveur
-- vérifie cette signature contre la clé publique enregistrée à l'enrôlement, et
-- la conserve. On peut alors répondre à « qui a validé ? » autrement que par
-- « quelqu'un qui connaissait un mot de passe ».
--
-- POURQUOI DES COLONNES ET NON UNE TABLE
-- La signature est un attribut de la validation, pas une entité : un
-- compte-rendu n'a qu'un seul acte de validation, et le jour où il est
-- dévalidé puis revalidé, c'est la nouvelle signature qui fait foi. Une table
-- séparée n'apporterait qu'une jointure de plus à chaque lecture.

ALTER TABLE reports ADD COLUMN IF NOT EXISTS signing_device_id  UUID REFERENCES mobile_devices(id) ON DELETE SET NULL;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS device_signature   TEXT;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS device_signed_at   TIMESTAMP;

-- Retrouver les validations signées par un appareil donné — la question qu'on
-- se posera le jour où un téléphone est perdu ou contesté.
CREATE INDEX IF NOT EXISTS idx_reports_appareil_signataire
    ON reports(signing_device_id) WHERE signing_device_id IS NOT NULL;

COMMENT ON COLUMN reports.signing_device_id IS
    'Appareil enrôlé ayant signé la validation. NULL pour une validation faite depuis le web.';
COMMENT ON COLUMN reports.device_signature IS
    'Signature base64 du condensé « validate:<reportId>:<userId>:<signedAt> », vérifiée à la réception.';
