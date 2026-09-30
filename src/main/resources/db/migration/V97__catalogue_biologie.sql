-- Module Biologie clinique : le catalogue.
--
-- CE QU'EST UNE ANALYSE DE BIOLOGIE
-- Une ligne de `lab_tests` en discipline BIOLOGY — la même que pour l'anapath,
-- afin que tarifs, contrats, factures, caisse et tableaux de bord la traitent
-- sans rien changer. Ce qui lui est propre vit dans les tables ci-dessous :
--   * PANEL   : une fiche de paramètres (sections, paramètres, valeurs de
--               référence par sexe et par âge, limites critiques) ;
--   * CULTURE : une liste d'options de culture, les antibiotiques de
--               l'antibiogramme étant un référentiel commun.
--
-- CE QUE CETTE MIGRATION NE TOUCHE PAS
-- Aucune ligne existante n'est modifiée : les deux colonnes ajoutées à
-- `lab_tests` sont nulles, et la contrainte qui les encadre est satisfaite par
-- toutes les analyses d'anatomie pathologique.
--
-- IDEMPOTENCE
-- Chaque ordre est rejouable (IF NOT EXISTS, contraintes vérifiées dans
-- pg_constraint) : une reprise après échec partiel ne bute sur rien.
--
-- UNICITÉ
-- Les tables pratiquent la suppression logique : l'unicité ne vaut que pour les
-- lignes vivantes (index partiels WHERE deleted_at IS NULL), pour qu'un nom
-- supprimé puisse être recréé.

-- ---------------------------------------------------------------------------
-- 1. lab_tests : nature d'une analyse de biologie et type d'échantillon.
-- ---------------------------------------------------------------------------
ALTER TABLE lab_tests ADD COLUMN IF NOT EXISTS biology_kind  VARCHAR(20)  NULL;
ALTER TABLE lab_tests ADD COLUMN IF NOT EXISTS specimen_type VARCHAR(100) NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_lab_tests_biology_kind') THEN
        ALTER TABLE lab_tests ADD CONSTRAINT chk_lab_tests_biology_kind
            CHECK (biology_kind IS NULL OR biology_kind IN ('PANEL', 'CULTURE'));
    END IF;
    -- Une analyse d'anatomie pathologique n'a pas de nature biologique.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_lab_tests_biology_kind_discipline') THEN
        ALTER TABLE lab_tests ADD CONSTRAINT chk_lab_tests_biology_kind_discipline
            CHECK (discipline = 'BIOLOGY' OR biology_kind IS NULL);
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Sections d'une fiche de paramètres (« Hémogramme », « Formule leucocytaire »).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_sections (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id   UUID         NOT NULL,
    lab_test_id UUID         NOT NULL REFERENCES lab_tests(id),
    title       VARCHAR(200) NOT NULL,
    position    INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_biology_sections_branch_id   ON biology_sections (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_sections_lab_test_id ON biology_sections (lab_test_id);

-- ---------------------------------------------------------------------------
-- 3. Paramètres : ce que l'on mesure ou observe.
--    NUMERIC → valeur chiffrée, comparée aux valeurs de référence ;
--    TEXT    → texte libre ;
--    CHOICE  → une valeur parmi `choices` (tableau JSON de libellés).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_parameters (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id           UUID         NOT NULL,
    lab_test_id         UUID         NOT NULL REFERENCES lab_tests(id),
    section_id          UUID         NULL     REFERENCES biology_sections(id),
    code                VARCHAR(50)  NULL,
    name                VARCHAR(200) NOT NULL,
    position            INTEGER      NOT NULL DEFAULT 0,
    result_type         VARCHAR(10)  NOT NULL,
    choices             JSONB        NULL,
    decimals            SMALLINT     NULL,
    unit_measurement_id UUID         NULL     REFERENCES unit_measurements(id),
    reference_text      TEXT         NULL,
    printable           BOOLEAN      NOT NULL DEFAULT TRUE,
    flaggable           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMP,
    CONSTRAINT chk_biology_parameters_result_type CHECK (result_type IN ('NUMERIC', 'TEXT', 'CHOICE')),
    CONSTRAINT chk_biology_parameters_decimals    CHECK (decimals IS NULL OR decimals BETWEEN 0 AND 6)
);
CREATE INDEX IF NOT EXISTS idx_biology_parameters_branch_id   ON biology_parameters (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_parameters_lab_test_id ON biology_parameters (lab_test_id);
CREATE INDEX IF NOT EXISTS idx_biology_parameters_section_id  ON biology_parameters (section_id);
CREATE INDEX IF NOT EXISTS idx_biology_parameters_unit_id     ON biology_parameters (unit_measurement_id);
-- Le code identifie un paramètre au sein de son analyse (import, PDF, formules).
CREATE UNIQUE INDEX IF NOT EXISTS uq_biology_parameters_code
    ON biology_parameters (lab_test_id, lower(code))
    WHERE deleted_at IS NULL AND code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 4. Valeurs de référence d'un paramètre NUMERIC.
--    Critères facultatifs : sexe, tranche d'âge en jours [age_min, age_max[.
--    Le plus spécifique l'emporte (voir ReferenceRangeResolver).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_reference_ranges (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id     UUID          NOT NULL,
    parameter_id  UUID          NOT NULL REFERENCES biology_parameters(id),
    sex           CHAR(1)       NULL,
    age_min_days  INTEGER       NULL,
    age_max_days  INTEGER       NULL,
    low           NUMERIC(14,4) NULL,
    high          NUMERIC(14,4) NULL,
    critical_low  NUMERIC(14,4) NULL,
    critical_high NUMERIC(14,4) NULL,
    label         VARCHAR(100)  NULL,
    position      INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMP,
    CONSTRAINT chk_biology_reference_ranges_sex CHECK (sex IS NULL OR sex IN ('M', 'F')),
    CONSTRAINT chk_biology_reference_ranges_age CHECK (
        (age_min_days IS NULL OR age_min_days >= 0)
        AND (age_min_days IS NULL OR age_max_days IS NULL OR age_min_days < age_max_days)),
    CONSTRAINT chk_biology_reference_ranges_bounds CHECK (low IS NULL OR high IS NULL OR low <= high)
);
CREATE INDEX IF NOT EXISTS idx_biology_reference_ranges_branch_id    ON biology_reference_ranges (branch_id);
CREATE INDEX IF NOT EXISTS idx_biology_reference_ranges_parameter_id ON biology_reference_ranges (parameter_id);

-- ---------------------------------------------------------------------------
-- 5. Antibiotiques de l'antibiogramme (référentiel de la succursale).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS antibiotics (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       UUID         NOT NULL,
    name            VARCHAR(150) NOT NULL,
    commercial_name VARCHAR(150) NULL,
    family          VARCHAR(100) NULL,
    code            VARCHAR(50)  NULL,
    position        INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_antibiotics_branch_id ON antibiotics (branch_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_antibiotics_name
    ON antibiotics (branch_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_antibiotics_code
    ON antibiotics (branch_id, lower(code))
    WHERE deleted_at IS NULL AND code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. Options de culture (« Aspect », « Germe isolé »…) et leurs choix (JSON).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biology_culture_options (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id  UUID         NOT NULL,
    name       VARCHAR(150) NOT NULL,
    choices    JSONB        NULL,
    position   INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_biology_culture_options_branch_id ON biology_culture_options (branch_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_biology_culture_options_name
    ON biology_culture_options (branch_id, lower(name))
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 7. Options de culture retenues par une analyse CULTURE, dans l'ordre d'affichage.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lab_test_culture_options (
    id                UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id         UUID      NOT NULL,
    lab_test_id       UUID      NOT NULL REFERENCES lab_tests(id),
    culture_option_id UUID      NOT NULL REFERENCES biology_culture_options(id),
    position          INTEGER   NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_lab_test_culture_options_branch_id  ON lab_test_culture_options (branch_id);
CREATE INDEX IF NOT EXISTS idx_lab_test_culture_options_option_id  ON lab_test_culture_options (culture_option_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lab_test_culture_options_pair
    ON lab_test_culture_options (lab_test_id, culture_option_id)
    WHERE deleted_at IS NULL;
