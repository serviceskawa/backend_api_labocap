-- Module Biologie clinique : la saisie des résultats.
--
-- À QUOI SE RATTACHE UN RÉSULTAT
-- Au couple (bon, analyse) — `test_order_id`, `lab_test_id` — et JAMAIS à la
-- ligne `detail_test_orders` : la modification d'un bon supprime et recrée toutes
-- ses lignes à chaque enregistrement, ce qui rendrait orphelin tout résultat qui
-- s'y accrocherait.
--
-- LES TABLES
--   * biology_analysis_results   : une ligne par analyse d'un bon de biologie
--                                  validé — son état (PENDING → ENTERED →
--                                  TECH_VALIDATED), son commentaire, qui a saisi
--                                  et qui a validé techniquement ;
--   * biology_parameter_results  : la valeur d'un paramètre (analyse PANEL),
--                                  l'indicateur calculé (N, L, H, LL, HH, ou A posé
--                                  à la main) et une COPIE de l'unité et des
--                                  valeurs de référence au moment de la saisie :
--                                  un catalogue modifié plus tard ne réécrit pas
--                                  un compte-rendu déjà rendu ;
--   * biology_culture_results    : la valeur retenue pour une option de culture ;
--   * biology_isolates           : les germes isolés d'une culture ;
--   * biology_antibiogram_results: l'antibiogramme d'un germe — S, I ou R. Le
--                                  libellé affiché (« Sensible »…) est un réglage
--                                  du laboratoire ; la valeur stockée reste S/I/R
--                                  pour que les données restent comparables.
--
-- CE QUE CETTE MIGRATION NE TOUCHE PAS
-- Aucune table existante n'est modifiée ; aucune ligne n'est insérée.
--
-- IDEMPOTENCE
-- Chaque ordre est rejouable (IF NOT EXISTS, contraintes vérifiées dans
-- pg_constraint) : une reprise après échec partiel ne bute sur rien.
--
-- UNICITÉ
-- Les tables pratiquent la suppression logique : l'unicité ne vaut que pour les
-- lignes vivantes (index partiels WHERE deleted_at IS NULL), pour qu'une analyse
-- retirée puis remise sur le bon, ou une valeur effacée puis ressaisie, retrouve
-- une ligne neuve.

-- ---------------------------------------------------------------------------
-- 1. Une ligne par analyse d'un bon de biologie validé.
--    ON DELETE CASCADE : seule une suppression PHYSIQUE du bon (jamais faite par
--    l'application, qui supprime logiquement) emporte ses résultats.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_analysis_results (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id          UUID        NOT NULL,
    test_order_id      UUID        NOT NULL REFERENCES test_orders(id) ON DELETE CASCADE,
    lab_test_id        UUID        NOT NULL REFERENCES lab_tests(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment            TEXT        NULL,
    entered_by         UUID        NULL,
    entered_at         TIMESTAMP   NULL,
    tech_validated_by  UUID        NULL,
    tech_validated_at  TIMESTAMP   NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMP,
    CONSTRAINT chk_biology_analysis_results_status
        CHECK (status IN ('PENDING', 'ENTERED', 'TECH_VALIDATED'))
);
CREATE INDEX IF NOT EXISTS idx_biology_analysis_results_branch_id     ON biology_analysis_results (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_analysis_results_test_order_id ON biology_analysis_results (test_order_id);
CREATE INDEX IF NOT EXISTS idx_biology_analysis_results_lab_test_id   ON biology_analysis_results (lab_test_id);
-- La liste de travail filtre la succursale par état.
CREATE INDEX IF NOT EXISTS idx_biology_analysis_results_branch_status
    ON biology_analysis_results (branch_id, status)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_biology_analysis_results_pair
    ON biology_analysis_results (test_order_id, lab_test_id)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 2. Valeur d'un paramètre (analyse PANEL).
--    value_text    : la valeur telle qu'elle sera imprimée (pour un NUMERIC, le
--                    nombre normalisé et arrondi ; pour TEXT/CHOICE, le texte) ;
--    value_numeric : le nombre, pour un NUMERIC seulement ;
--    flag          : N, L, H, LL, HH — ou A (« anormal »), posé à la main sur un
--                    TEXT/CHOICE ; NULL = pas d'indicateur ;
--    *_snapshot    : unité et valeurs de référence copiées à la saisie.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_parameter_results (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id               UUID          NOT NULL,
    analysis_result_id      UUID          NOT NULL REFERENCES biology_analysis_results(id) ON DELETE CASCADE,
    parameter_id            UUID          NOT NULL REFERENCES biology_parameters(id),
    value_text              TEXT          NULL,
    value_numeric           NUMERIC(14,4) NULL,
    flag                    VARCHAR(2)    NULL,
    flag_overridden         BOOLEAN       NOT NULL DEFAULT FALSE,
    unit_snapshot           VARCHAR(100)  NULL,
    low_snapshot            NUMERIC(14,4) NULL,
    high_snapshot           NUMERIC(14,4) NULL,
    critical_low_snapshot   NUMERIC(14,4) NULL,
    critical_high_snapshot  NUMERIC(14,4) NULL,
    reference_snapshot      TEXT          NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    deleted_at              TIMESTAMP,
    CONSTRAINT chk_biology_parameter_results_flag
        CHECK (flag IS NULL OR flag IN ('N', 'L', 'H', 'LL', 'HH', 'A'))
);
CREATE INDEX IF NOT EXISTS idx_biology_parameter_results_branch_id    ON biology_parameter_results (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_parameter_results_analysis_id  ON biology_parameter_results (analysis_result_id);
CREATE INDEX IF NOT EXISTS idx_biology_parameter_results_parameter_id ON biology_parameter_results (parameter_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_biology_parameter_results_pair
    ON biology_parameter_results (analysis_result_id, parameter_id)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Valeur retenue pour une option de culture (analyse CULTURE).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_culture_results (
    id                 UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id          UUID      NOT NULL,
    analysis_result_id UUID      NOT NULL REFERENCES biology_analysis_results(id) ON DELETE CASCADE,
    culture_option_id  UUID      NOT NULL REFERENCES biology_culture_options(id),
    value              TEXT      NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_biology_culture_results_branch_id   ON biology_culture_results (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_culture_results_analysis_id ON biology_culture_results (analysis_result_id);
CREATE INDEX IF NOT EXISTS idx_biology_culture_results_option_id   ON biology_culture_results (culture_option_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_biology_culture_results_pair
    ON biology_culture_results (analysis_result_id, culture_option_id)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 4. Germes isolés d'une culture, dans l'ordre d'affichage.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_isolates (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id          UUID         NOT NULL,
    analysis_result_id UUID         NOT NULL REFERENCES biology_analysis_results(id) ON DELETE CASCADE,
    organism           VARCHAR(200) NOT NULL,
    quantity           VARCHAR(100) NULL,
    position           INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_biology_isolates_branch_id   ON biology_isolates (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_isolates_analysis_id ON biology_isolates (analysis_result_id);

-- ---------------------------------------------------------------------------
-- 5. Antibiogramme d'un germe : S, I ou R, CMI et diamètre facultatifs.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_antibiogram_results (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id      UUID         NOT NULL,
    isolate_id     UUID         NOT NULL REFERENCES biology_isolates(id) ON DELETE CASCADE,
    antibiotic_id  UUID         NOT NULL REFERENCES antibiotics(id),
    interpretation CHAR(1)      NOT NULL,
    mic            VARCHAR(20)  NULL,
    diameter_mm    NUMERIC(5,1) NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMP
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_biology_antibiogram_results_interpretation') THEN
        ALTER TABLE biology_antibiogram_results ADD CONSTRAINT chk_biology_antibiogram_results_interpretation
            CHECK (interpretation IN ('S', 'I', 'R'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_biology_antibiogram_results_diameter') THEN
        ALTER TABLE biology_antibiogram_results ADD CONSTRAINT chk_biology_antibiogram_results_diameter
            CHECK (diameter_mm IS NULL OR diameter_mm >= 0);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_biology_antibiogram_results_branch_id     ON biology_antibiogram_results (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_antibiogram_results_isolate_id    ON biology_antibiogram_results (isolate_id);
CREATE INDEX IF NOT EXISTS idx_biology_antibiogram_results_antibiotic_id ON biology_antibiogram_results (antibiotic_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_biology_antibiogram_results_pair
    ON biology_antibiogram_results (isolate_id, antibiotic_id)
    WHERE deleted_at IS NULL;
