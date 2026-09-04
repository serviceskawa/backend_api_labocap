-- Trace de l'avis automatique de disponibilité envoyé au patient.
--
-- CONTEXTE
-- À la validation d'un compte-rendu, le patient est prévenu que son résultat est
-- disponible au retrait : appel vocal OurVoice, ou SMS si le bon d'examen le
-- demande. L'appel n'est permis qu'entre 8h et 18h — on ne réveille pas un
-- patient. Un compte-rendu validé un vendredi à 19h ne déclenchait donc rien, et
-- restait muet tout le week-end faute d'un clic d'agent le lundi.
--
-- Une tâche planifiée rattrape désormais ces avis le lendemain à 8h. Encore
-- faut-il savoir lesquels sont partis : c'est cette colonne.
--
-- POURQUOI PAS is_called
-- La colonne is_called existe, mais elle dit autre chose : un agent la coche à
-- la main depuis l'écran de suivi (« patient informé »), et la remise du
-- compte-rendu la pose aussi. La confondre avec l'avis automatique fausserait le
-- décompte « informés / non informés » de l'écran de suivi, qui compte des
-- gestes humains.
--
-- BACKFILL — LE POINT IMPORTANT
-- Sans lui, la première exécution à 8h prendrait les milliers de comptes-rendus
-- déjà validés pour des avis en attente et appellerait tous ces patients d'un
-- coup, des mois après leur examen. Tout ce qui est déjà validé ou remis est
-- donc marqué comme traité : la reprise ne notifie que ce qui sera validé après
-- sa mise en service.

ALTER TABLE reports ADD COLUMN IF NOT EXISTS patient_notified_at TIMESTAMP;

UPDATE reports
   SET patient_notified_at = COALESCE(signature_date, updated_at, created_at, NOW())
 WHERE status IN ('VALIDATED', 'DELIVERED')
   AND patient_notified_at IS NULL;

-- Index partiel : la tâche de 8h ne cherche que les avis en attente, une poignée
-- de lignes face aux dizaines de milliers déjà traitées.
CREATE INDEX IF NOT EXISTS idx_reports_avis_patient_en_attente
    ON reports (signature_date) WHERE patient_notified_at IS NULL;

COMMENT ON COLUMN reports.patient_notified_at IS 'Date d''envoi de l''avis automatique de disponibilité au patient (appel OurVoice ou SMS). NULL = aucun avis parti ; la tâche de rattrapage de 8h ne traite que ces lignes. Distincte de is_called, qui trace un geste humain.';
