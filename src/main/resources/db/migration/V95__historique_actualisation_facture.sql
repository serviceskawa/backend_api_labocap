-- Trace chaque actualisation manuelle du nom/adresse client d'une facture
-- depuis les informations courantes du patient rattaché (qui, quand, avant → après).
CREATE TABLE invoice_client_info_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID NOT NULL REFERENCES branches(id),
    invoice_id          UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    user_id             UUID NULL REFERENCES users(id),
    old_client_name     VARCHAR(255) NULL,
    new_client_name     VARCHAR(255) NULL,
    old_client_address  VARCHAR(255) NULL,
    new_client_address  VARCHAR(255) NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_client_info_history_branch_id  ON invoice_client_info_history(branch_id);
CREATE INDEX idx_invoice_client_info_history_invoice_id ON invoice_client_info_history(invoice_id);
