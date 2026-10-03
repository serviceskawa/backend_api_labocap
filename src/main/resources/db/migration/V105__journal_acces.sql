-- Journal des consultations (lot 6) : qui a LU quoi, et quand.
--
-- log_reports trace les actions sur les comptes rendus (création, validation,
-- remise…) mais rien ne disait qui avait ouvert un dossier patient, une
-- demande, un compte rendu, une facture ou un fichier. Table en ajout seul :
-- aucune route ne modifie ni ne supprime ; seule la purge mensuelle (12 mois)
-- y touche.
CREATE TABLE journal_acces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    at              TIMESTAMP NOT NULL DEFAULT NOW(),
    user_id         UUID NOT NULL,
    branch_id       UUID,
    action          VARCHAR(20) NOT NULL,   -- READ, DOWNLOAD, PRINT, EXPORT
    entity_type     VARCHAR(20) NOT NULL,   -- PATIENT, TEST_ORDER, REPORT, FILE, INVOICE
    -- Identifiant de l'entité, ou chemin relatif pour un fichier.
    entity_id       VARCHAR(255) NOT NULL,
    ip              VARCHAR(64),
    user_agent_hash VARCHAR(64)
);
CREATE INDEX idx_journal_acces_entite ON journal_acces (entity_type, entity_id);
CREATE INDEX idx_journal_acces_user ON journal_acces (user_id, at);

-- Lecture du journal : une permission à part, donnée aux administrateurs.
INSERT INTO permissions (id, name, slug, created_at, updated_at)
SELECT gen_random_uuid(), 'Consulter le journal des accès', 'view-audit', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE slug = 'view-audit');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug IN ('super-admin', 'admin')
  AND p.slug = 'view-audit'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = p.id
  );
