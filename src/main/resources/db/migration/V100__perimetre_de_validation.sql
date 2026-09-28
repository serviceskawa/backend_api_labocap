-- Validation d'un compte-rendu restreinte à certains types d'examen.
--
-- Jusqu'ici la permission « validate-reports » était tout ou rien : qui la
-- portait validait n'importe quel compte-rendu. Le laboratoire veut confier au
-- secrétariat la validation de quelques types seulement — un compte-rendu
-- d'immuno externe transcrit le résultat d'un autre laboratoire, il n'appelle
-- pas le même jugement qu'une biopsie.
--
-- Le périmètre est porté par le COMPTE et non par le rôle : deux secrétaires
-- n'ont pas la même expérience, et le rôle « secretariat » les confondrait.

CREATE TABLE perimetre_de_validation (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id      UUID NOT NULL REFERENCES branches(id),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type_order_id  UUID NOT NULL REFERENCES type_orders(id) ON DELETE CASCADE,
    -- Qui a accordé ce périmètre : l'attribution d'un droit de validation est
    -- elle-même un acte dont on doit pouvoir répondre.
    accorde_par    UUID NULL REFERENCES users(id),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_perimetre_validation_user_type UNIQUE (user_id, type_order_id)
);

CREATE INDEX idx_perimetre_de_validation_user_id   ON perimetre_de_validation(user_id);
CREATE INDEX idx_perimetre_de_validation_branch_id ON perimetre_de_validation(branch_id);

-- Qui a réellement validé.
--
-- `signatory1..3` désignent les pathologistes dont la signature figure au
-- document, et `reviewed_by_user_id` le relecteur : aucun des deux ne dit qui a
-- posé l'acte de validation. Tant que seuls des médecins validaient, la question
-- ne se posait pas. Dès lors qu'un secrétaire peut le faire, le compte-rendu
-- doit porter son nom — c'est la contrepartie de l'ouverture, et sans elle un
-- document affirmerait une validation médicale qui n'a pas eu lieu.
ALTER TABLE reports ADD COLUMN IF NOT EXISTS validated_by_user_id UUID NULL REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_reports_validated_by_user_id ON reports(validated_by_user_id);

-- Les comptes-rendus déjà validés le restent sans nom : la trace existe dans
-- le journal (`report_actions`), et inventer un validateur ici serait écrire
-- une information que personne n'a constatée.
