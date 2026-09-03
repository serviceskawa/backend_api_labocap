-- Un libellé de facture choisi à la main, ligne par ligne.
--
-- Pourquoi une colonne de plus plutôt que de modifier « test_name » : celui-ci
-- est recopié du catalogue à l'émission et sert de trace de ce qui a été
-- facturé. L'écraser ferait perdre l'analyse réellement rendue, et les
-- statistiques comme les rapprochements avec le catalogue s'en trouveraient
-- faussés — sans qu'on puisse revenir en arrière.
--
-- Vide, la facture montre « test_name » comme aujourd'hui. Rempli, c'est lui
-- qui paraît, sur le document comme dans la requête de normalisation à la DGI.
ALTER TABLE invoice_details
    ADD COLUMN IF NOT EXISTS custom_test_name varchar(100);
