-- « Commentaire » de clôture de la caisse de vente (récapitulatif + impression).
-- Saisi à l'étape 2 de la fermeture (obligatoire en cas d'écart) mais jusqu'ici
-- jamais persisté : la colonne rétablit le round-trip vers le récap et le print.
ALTER TABLE cashbox_dailies ADD COLUMN IF NOT EXISTS description TEXT;
