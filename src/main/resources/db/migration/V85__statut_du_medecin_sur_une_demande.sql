-- Où en est le médecin sur une demande qui lui est affectée.
--
-- Distinct du statut du compte rendu, et volontairement. Celui-ci dit où en est
-- le document — brouillon, en relecture, validé, remis. Celui-là dit où en est
-- la personne : elle ne l'a pas encore ouvert, elle s'en occupe, elle en a
-- fini. Les deux avancent à des rythmes différents, et confondre « j'ai fini de
-- lire » avec « le compte rendu est validé » ferait disparaître de la file de
-- travail un dossier encore à écrire.
--
-- Trois valeurs seulement, écrites en clair plutôt qu'en type énuméré
-- PostgreSQL : en ajouter une quatrième ne doit pas demander une migration de
-- type, que Flyway rend malcommode. La contrainte les borne quand même.

ALTER TABLE test_order_assignment_details
    ADD COLUMN IF NOT EXISTS docteur_status VARCHAR(20) NOT NULL DEFAULT 'a_traiter';

ALTER TABLE test_order_assignment_details
    DROP CONSTRAINT IF EXISTS chk_docteur_status;
ALTER TABLE test_order_assignment_details
    ADD CONSTRAINT chk_docteur_status
    CHECK (docteur_status IN ('a_traiter', 'pris_en_charge', 'termine'));

-- La file de travail d'un médecin se lit par son affectation, du plus ancien au
-- plus récent : c'est la requête que « Mes demandes » exécute à chaque
-- ouverture.
CREATE INDEX IF NOT EXISTS idx_assignment_details_statut
    ON test_order_assignment_details (test_order_assignment_id, docteur_status);
