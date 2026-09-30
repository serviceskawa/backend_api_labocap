-- Traçabilité du droit de valider.
--
-- `perimetre_de_validation` ne porte que l'état courant, et l'enregistrement
-- d'un périmètre efface puis réécrit ses lignes. Accorder « Cytologie » à un
-- secrétaire le lundi, le laisser valider quarante comptes-rendus, puis retirer
-- le type le vendredi ne laissait donc aucune trace que l'autorisation ait
-- jamais existé — et un compte-rendu validé se serait retrouvé sans règle
-- visible pour l'expliquer.
--
-- Accorder à quelqu'un le droit de poser un acte médical est lui-même un acte.
-- Il se journalise comme le reste.

CREATE TABLE journal_du_perimetre_de_validation (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id      UUID NOT NULL REFERENCES branches(id),
    -- Le compte dont le périmètre a changé.
    user_id        UUID NOT NULL REFERENCES users(id),
    -- Qui a décidé. Nul si le compte a disparu depuis : on préfère garder la
    -- ligne sans son auteur plutôt que de perdre le fait.
    accorde_par    UUID NULL REFERENCES users(id),
    -- Avant et après, en clair, séparés par des virgules. Le libellé plutôt
    -- qu'un identifiant : ce journal se lit des années plus tard, quand la
    -- ligne `type_orders` visée peut avoir été renommée ou supprimée, et un
    -- UUID orphelin ne dirait plus rien à personne.
    types_avant    TEXT NOT NULL DEFAULT '',
    types_apres    TEXT NOT NULL DEFAULT '',
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journal_perimetre_user   ON journal_du_perimetre_de_validation(user_id, created_at DESC);
CREATE INDEX idx_journal_perimetre_branch ON journal_du_perimetre_de_validation(branch_id);

-- Les périmètres déjà posés n'ont pas d'antériorité : leur ligne
-- `perimetre_de_validation` porte déjà `accorde_par` et `created_at`. Inventer
-- ici une entrée de journal reviendrait à dater une décision que personne n'a
-- observée.
