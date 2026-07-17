-- V55: Date de facture (réplique Laravel invoices.date)
-- Laravel déclare `$table->date('date')->nullable()` : date saisie à la création,
-- distincte de created_at. La colonne manquait au schéma de la refonte, ce qui
-- bloquait la validation Hibernate au démarrage.
-- Les factures antérieures restent à NULL : la donnée Laravel d'origine n'a pas
-- été recopiée lors de la migration et n'est pas récupérable ici.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS date date;
