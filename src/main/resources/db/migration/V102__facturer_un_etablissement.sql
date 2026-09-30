-- Qui paie n'est pas toujours qui est soigné.
--
-- Une clinique adresse son patient au laboratoire et règle l'examen ; la
-- facture doit alors porter son nom, son adresse et son IFU, et la déclaration
-- à la DGI doit dire la même chose. Jusqu'ici seules deux voies existaient :
-- la facture allait au patient, ou bien au client d'un contrat groupé — ce qui
-- impose une facture cumulative et ne se décide pas demande par demande.
--
-- L'intention se pose donc sur la demande, et le résultat sur la facture.

ALTER TABLE test_orders ADD COLUMN IF NOT EXISTS facture_a_nom     varchar(150);
ALTER TABLE test_orders ADD COLUMN IF NOT EXISTS facture_a_adresse text;
ALTER TABLE test_orders ADD COLUMN IF NOT EXISTS facture_a_ifu     varchar(50);

COMMENT ON COLUMN test_orders.facture_a_nom IS
  'Établissement à facturer à la place du patient. NULL = le patient paie, ce qui reste le cas ordinaire.';
COMMENT ON COLUMN test_orders.facture_a_ifu IS
  'Identifiant fiscal de cet établissement, transmis à la DGI. Vide accepté : une facture peut partir sans, la DGI ne l''exige que pour identifier un acheteur professionnel.';

-- L'IFU manquait à la facture, et donc à la déclaration : « acheteur() »
-- envoyait le nom et l'adresse, jamais l'identifiant fiscal. Une vente à un
-- établissement partait ainsi sans dire à qui, alors même que le client d'un
-- contrat en portait un en base.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS client_ifu varchar(50);

COMMENT ON COLUMN invoices.client_ifu IS
  'IFU de l''acheteur, transmis à la DGI avec son nom et son adresse.';

-- La validation d'une demande réécrit l'identité de la facture depuis le
-- patient, à chaque fois. Sans cette marque, une adresse de facturation saisie
-- à la main serait effacée au geste suivant, sans que rien ne le signale.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS facturation_figee boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN invoices.facturation_figee IS
  'Vraie quand l''identité de facturation a été saisie à la main : la revalidation de la demande ne la réécrit plus.';
