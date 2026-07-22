-- Champs RH manquants pour aligner le module Équipe sur l'app Laravel d'origine.
-- Profil employé : identité étendue (naissance, adresse, CNSS, photo, sexe…).
-- Contrat employé : onglet « Contrat » (période d'essai, horaires) + onglet
-- « Paie » (taux horaire, transport, coordonnées bancaires).

ALTER TABLE employees ADD COLUMN IF NOT EXISTS address        VARCHAR(255);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS date_of_birth  DATE;
ALTER TABLE employees ADD COLUMN IF NOT EXISTS place_of_birth VARCHAR(200);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS cnss_number    VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS photo_url      VARCHAR(500);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS gender         VARCHAR(20);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS nationality    VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS city           VARCHAR(100);

ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS probation_end_date    DATE;
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS weekly_work_hours     INTEGER;
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS working_days_per_week INTEGER;
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS termination_reason    TEXT;
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS hourly_gross_rate     NUMERIC(10,2);
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS transport_allowance   NUMERIC(10,2);
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS iban                  VARCHAR(50);
ALTER TABLE employee_contrats ADD COLUMN IF NOT EXISTS bic                   VARCHAR(20);
