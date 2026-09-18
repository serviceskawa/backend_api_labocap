-- Trace de l'envoi du SMS de facture, pour n'en envoyer qu'un.
--
-- CONTEXTE
-- Le SMS annonçant qu'une facture est téléchargeable partait jusqu'ici de la
-- seule normalisation FluidInvoice. Il part désormais de tout geste qui valide
-- la facture : l'encaissement en caisse, la normalisation MECeF par machine
-- e-MECeF, et celle par FluidInvoice — voir InvoiceValidatedEvent.
--
-- POURQUOI UNE COLONNE PLUTÔT QUE RIEN
-- Une facture encaissée puis normalisée traverse deux de ces gestes : sans
-- marque, le client recevrait deux SMS pour une seule facture, et le
-- laboratoire paierait deux envois. La présence du jeton de partage ne peut
-- pas tenir ce rôle : il est émis avant l'appel à OurVoice, si bien qu'un envoi
-- échoué passerait ensuite pour un envoi réussi et ne serait jamais retenté.
--
-- NULL se lit « aucun SMS n'est encore parti » — l'état de toutes les factures
-- déjà en base, qu'aucune reprise ne doit venir notifier après coup.

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS share_sms_sent_at TIMESTAMP;

COMMENT ON COLUMN invoices.share_sms_sent_at IS 'Date d''envoi au client du SMS portant le lien de téléchargement. NULL tant qu''aucun SMS n''est parti ; renseignée, elle interdit tout nouvel envoi automatique.';
