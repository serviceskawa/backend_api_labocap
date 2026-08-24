-- Journal des actes accomplis depuis l'application mobile.
--
-- `log_reports` ne couvre que ce qui touche un compte rendu. Or l'essentiel de
-- ce qu'un agent fait au téléphone n'en relève pas : ouvrir une session,
-- enrôler l'appareil, joindre une photo à un bon, composer une affectation. Ces
-- actes ne laissaient aucune trace attribuable.
--
-- On enregistre QUI, DEPUIS QUEL APPAREIL, QUOI et QUAND. Pas le corps des
-- requêtes : il porte des contenus médicaux, des signatures et des codes PIN,
-- qui n'ont rien à faire dans un journal — la méthode et le chemin disent la
-- nature de l'acte, et la ligne visée se retrouve par son identifiant.

CREATE TABLE IF NOT EXISTS mobile_action_logs (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL,
    device_id   uuid,
    branch_id   uuid,
    methode     VARCHAR(10) NOT NULL,
    chemin      VARCHAR(500) NOT NULL,
    statut      INTEGER     NOT NULL,
    occurred_at TIMESTAMP   NOT NULL
);

-- Les deux questions qu'on pose à un journal : « qu'a fait cette personne ? »
-- et « que s'est-il passé ce jour-là ? ».
CREATE INDEX IF NOT EXISTS idx_mobile_action_logs_user
    ON mobile_action_logs (user_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_mobile_action_logs_date
    ON mobile_action_logs (occurred_at DESC);
