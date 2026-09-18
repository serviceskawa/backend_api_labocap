-- Lien public de téléchargement d'une facture, envoyé par SMS au client.
--
-- CONTEXTE
-- À la normalisation MECeF/DGI d'une facture, le client reçoit désormais un SMS
-- (OurVoice) contenant un lien de téléchargement du PDF. Le destinataire n'a pas
-- de compte : le lien doit donc s'ouvrir sans authentification, ce qu'aucune
-- route de l'API ne permettait jusqu'ici pour une facture.
--
-- POURQUOI UN JETON PLUTÔT QUE L'IDENTIFIANT DE LA FACTURE
-- Servir /public/invoices/{id} exposerait chaque facture à qui connaît un UUID —
-- et surtout, un identifiant ne peut pas expirer ni être révoqué. Le jeton est
-- tiré au sort (24 octets, SecureRandom), n'a aucun lien déductible avec la
-- facture, et porte sa propre date d'expiration.
--
-- POURQUOI SUR invoices ET PAS DANS UNE TABLE DÉDIÉE
-- Un seul lien vit à la fois par facture : le SMS le rejoue tel quel s'il est
-- renvoyé, et le régénérer invaliderait le lien déjà reçu par le client. Une
-- table de liens n'apporterait qu'un historique dont personne n'a l'usage.
--
-- Colonnes nullables : les factures déjà en base n'ont pas de lien public, et
-- ne doivent pas en avoir tant que personne ne le demande. « Pas de lien » se
-- lit comme NULL.

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_token            VARCHAR(64);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_token_expires_at TIMESTAMP;

-- Index unique partiel : le jeton est la clé de recherche de la route publique,
-- et deux factures ne peuvent pas partager le même. Partiel (WHERE NOT NULL)
-- pour que les milliers de factures sans lien ne se heurtent pas sur NULL.
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_share_token
    ON invoices (share_token) WHERE share_token IS NOT NULL;

COMMENT ON COLUMN invoices.share_token            IS 'Jeton aléatoire du lien public de téléchargement envoyé par SMS. NULL tant qu''aucun lien n''a été émis.';
COMMENT ON COLUMN invoices.share_token_expires_at IS 'Expiration du lien public. Passée cette date, la route publique refuse le jeton (410).';
