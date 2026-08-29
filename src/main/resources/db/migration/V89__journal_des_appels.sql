-- La trace des appels audio passés depuis un fil de discussion.
--
-- Ce que la table garde : qui, quand, combien de temps, et comment l'appel
-- s'est terminé. Ce qu'elle ne garde pas : la voix. Un appel entre un médecin
-- et un technicien porte un avis médical ; l'enregistrer demanderait le
-- consentement de chacun et créerait un fonds d'archives plus sensible que tout
-- le reste de cette base.
CREATE TABLE IF NOT EXISTS call_logs (
    id             uuid PRIMARY KEY,
    test_order_id  uuid NOT NULL,
    branch_id      uuid,
    initiateur_id  uuid NOT NULL,
    -- Les conviés, séparés par des virgules : cette colonne ne se lit qu'en
    -- entier, pour afficher « appel entre untel et untel ».
    participants   varchar(1024),
    debut          timestamp NOT NULL,
    fin            timestamp,
    secondes       bigint,
    issue          varchar(32)
);

-- L'accès se fait toujours par dossier, jamais par personne : c'est en rouvrant
-- un cas qu'on veut savoir s'il a été discuté de vive voix.
CREATE INDEX IF NOT EXISTS idx_call_logs_test_order
    ON call_logs (test_order_id, debut DESC);
