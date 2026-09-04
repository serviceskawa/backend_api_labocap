-- La discussion attachée à un dossier.
--
-- Les médecins et les techniciens se parlent aujourd'hui hors de l'application
-- — au téléphone, dans un couloir. Ce qui se dit d'un cas n'est donc rattaché à
-- rien : impossible de savoir, six mois plus tard, pourquoi une lame a été
-- recolorée ou qui avait demandé quoi.
--
-- Un fil par dossier, jamais par personne : la conversation appartient au cas.
-- Deux médecins qui se relaient sur une demande retrouvent le même fil, et
-- celui qui arrive lit ce qui a précédé.

CREATE TABLE IF NOT EXISTS discussions (
    id             uuid PRIMARY KEY,
    test_order_id  uuid      NOT NULL UNIQUE,
    branch_id      uuid,
    created_at     TIMESTAMP NOT NULL
);

-- Qui participe, et à quel titre.
--
-- Le rôle est figé au moment où la personne rejoint le fil : il dit sous quelle
-- casquette elle a parlé ce jour-là. Le relire depuis ses rôles actuels ferait
-- changer l'attribution d'un message ancien le jour d'une promotion.
CREATE TABLE IF NOT EXISTS discussion_participants (
    id            uuid PRIMARY KEY,
    discussion_id uuid        NOT NULL REFERENCES discussions (id),
    user_id       uuid        NOT NULL,
    role          VARCHAR(20) NOT NULL,
    added_at      TIMESTAMP   NOT NULL,
    UNIQUE (discussion_id, user_id)
);

CREATE TABLE IF NOT EXISTS discussion_messages (
    id             uuid PRIMARY KEY,
    discussion_id  uuid        NOT NULL REFERENCES discussions (id),
    author_id      uuid        NOT NULL,
    type           VARCHAR(10) NOT NULL,
    -- Le texte, ou le chemin d'une photo ou d'une note vocale. Une seule
    -- colonne : les trois s'excluent, et trois colonnes dont deux toujours
    -- vides diraient le contraire.
    content        TEXT        NOT NULL,
    -- Le destinataire nommément visé, s'il y en a un. Nul = le groupe.
    tagged_user_id uuid,
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT chk_message_type CHECK (type IN ('texte', 'photo', 'audio'))
);

-- Qui a lu quoi.
--
-- Une ligne par lecture plutôt qu'un drapeau sur le message : un fil a
-- plusieurs participants, et « lu » n'a de sens que pour quelqu'un.
CREATE TABLE IF NOT EXISTS discussion_lectures (
    message_id uuid      NOT NULL REFERENCES discussion_messages (id),
    user_id    uuid      NOT NULL,
    lu_le      TIMESTAMP NOT NULL,
    PRIMARY KEY (message_id, user_id)
);

-- Les deux questions qu'on pose : « le fil de ce dossier » et « ce que je n'ai
-- pas lu », cette dernière à chaque ouverture de l'accueil.
CREATE INDEX IF NOT EXISTS idx_discussion_messages_fil
    ON discussion_messages (discussion_id, created_at);
CREATE INDEX IF NOT EXISTS idx_discussion_participants_user
    ON discussion_participants (user_id);
