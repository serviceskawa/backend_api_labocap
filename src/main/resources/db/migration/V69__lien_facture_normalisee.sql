-- Lien du document de la facture normalisée (FluidInvoice).
--
-- RENUMÉROTÉE DE V67 EN V69
-- Deux branches ont créé un V67 en parallèle — celle-ci et la signature
-- d'appareil du socle mobile — et Flyway refuse de démarrer sur un doublon de
-- version : « Found more than one migration with version 67 ». Le serveur
-- repartait en boucle sans jamais s'ouvrir.
--
-- C'est celle-ci qui bouge, et non l'autre : la signature d'appareil (V67) et
-- le droit d'images (V68) sont déjà appliqués sur les environnements, alors que
-- ce fichier n'a jamais pu l'être. Renuméroter une migration déjà passée aurait
-- laissé une ligne orpheline dans l'historique de Flyway.
--
-- CONTEXTE
-- La normalisation passait jusqu'ici par une saisie manuelle : l'agent recopiait
-- le code MECeF/DGI à 24 caractères dans la page facture, puis « Terminer la
-- facture ». Le nouveau flow appelle FluidInvoice (invoice.fluidpay.link), qui
-- orchestre les trois étapes e-MCF et renvoie, en plus du code, un lien vers le
-- document représentant la facture normalisée.
--
-- POURQUOI PERSISTER LE LIEN
-- Le flow demande que la première normalisation ouvre ce document dans un nouvel
-- onglet, et que les visites ultérieures montrent un bouton « voir la facture
-- normalisée ». Ce second point n'est tenable que si le lien survit à la
-- requête : sans cette colonne, l'utilisateur qui revient sur la facture aurait
-- perdu le seul accès au document.
--
-- POURQUOI AUSSI L'IDENTIFIANT FLUIDINVOICE
-- L'API accepte, pour émettre un avoir, soit le codeMECeFDGI d'origine (déjà
-- stocké dans `code_mecef`), soit `original_invoice_id`, son propre UUID. Le
-- conserver évite de dépendre du seul code MECeF, et ouvre l'accès aux routes
-- de consultation et de vérification d'intégrité (`/v1/invoices/{id}/verify`).
--
-- Colonnes nullables et sans valeur par défaut : les 12 000 factures déjà en
-- base n'ont pas été émises par FluidInvoice et ne doivent pas prétendre l'avoir
-- été. « Non normalisée » se lit ici comme NULL, jamais comme chaîne vide.

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS normalized_url  TEXT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS fluidinvoice_id VARCHAR(36);

COMMENT ON COLUMN invoices.normalized_url  IS 'Lien FluidInvoice vers le document de la facture normalisée. NULL tant que la facture n''a pas été normalisée.';
COMMENT ON COLUMN invoices.fluidinvoice_id IS 'Identifiant interne FluidInvoice de la facture, pour les avoirs (original_invoice_id) et la vérification d''intégrité.';
