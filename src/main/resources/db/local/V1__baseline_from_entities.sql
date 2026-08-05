-- ─────────────────────────────────────────────────────────────────────────────
-- Baseline LOCALE — schéma reconstruit depuis les 74 entités JPA.
--
-- ⚠ RÉSERVÉ AU PROFIL `local`. Ne jamais appliquer en production : la base de
-- production a son propre historique Flyway (baseline-version: 50) et un schéma
-- issu de la migration depuis l'ancienne application Laravel.
--
-- POURQUOI CE FICHIER
-- La chaîne V1→V61 de db/migration n'est pas rejouable sur une base vierge :
-- 27 des 61 migrations échouent (V10 `permissions.branch_id` inexistante, V18
-- `report_id`, V19 contrainte ON CONFLICT absente, V20 `deleted_at`, …). Ces
-- migrations forment un journal de correctifs appliqué à une base qui existait
-- déjà — pas un script de création. D'où cette baseline, générée par Hibernate
-- à partir des entités, qui sont la référence réelle du schéma attendu par
-- l'application (`ddl-auto: validate` les compare à la base à chaque démarrage).
--
-- RÉGÉNÉRATION après ajout ou modification d'entités :
--   ./scripts/regen-local-baseline.sh
--
-- LIMITES ASSUMÉES : Hibernate ne restitue ni les index métier, ni les
-- contraintes CHECK, ni les triggers de la base de production. Suffisant pour
-- développer en local, inadapté pour restaurer des données réelles.
-- ─────────────────────────────────────────────────────────────────────────────


CREATE TABLE public.appel_by_reports (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    report_id uuid NOT NULL,
    updated_by uuid,
    appel_id character varying(255) NOT NULL
);

CREATE TABLE public.appointments (
    created_at timestamp(6) without time zone NOT NULL,
    date timestamp(6) without time zone,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    patient_id uuid NOT NULL,
    updated_by uuid,
    user_id uuid,
    priority character varying(20),
    status character varying(20),
    message text
);

CREATE TABLE public.articles (
    expiration_date date,
    minimum_stock numeric(10,2) NOT NULL,
    purchase_price numeric(10,2) NOT NULL,
    quantity numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    supplier_id uuid,
    updated_by uuid,
    code character varying(50),
    unit character varying(50),
    lot_number character varying(100),
    name character varying(300) NOT NULL,
    description text
);

CREATE TABLE public.bank_deposits (
    amount numeric(12,2) NOT NULL,
    date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    bank_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    cashbox_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    attachement character varying(500),
    description text
);

CREATE TABLE public.banks (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    account_number character varying(100) NOT NULL,
    name character varying(200) NOT NULL,
    description text
);

CREATE TABLE public.branch_user (
    branch_id uuid NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE public.branches (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    code character varying(100),
    location character varying(200),
    name character varying(200) NOT NULL
);

CREATE TABLE public.cashbox_dailies (
    cash_calculated numeric(12,2),
    cash_confirmation numeric(12,2),
    cash_ecart numeric(12,2),
    cheque_calculated numeric(12,2),
    cheque_confirmation numeric(12,2),
    cheque_ecart numeric(12,2),
    closing_balance numeric(12,2) NOT NULL,
    date date NOT NULL,
    mobile_money_calculated numeric(12,2),
    mobile_money_confirmation numeric(12,2),
    mobile_money_ecart numeric(12,2),
    opening_balance numeric(12,2) NOT NULL,
    status integer,
    total_calculated numeric(12,2),
    total_confirmation numeric(12,2),
    total_ecart numeric(12,2),
    virement_calculated numeric(12,2),
    virement_confirmation numeric(12,2),
    virement_ecart numeric(12,2),
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    cashbox_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    code character varying(50),
    description text
);

CREATE TABLE public.cashbox_operations (
    amount numeric(12,2) NOT NULL,
    operation_date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    type character varying(10) NOT NULL,
    bank_id uuid,
    branch_id uuid NOT NULL,
    cashbox_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    invoice_id uuid,
    updated_by uuid,
    payment_method character varying(20),
    cheque_number character varying(50),
    reference character varying(100),
    attachement character varying(500),
    description text
);

CREATE TABLE public.cashbox_tickets (
    amount numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    type character varying(10) NOT NULL,
    branch_id uuid NOT NULL,
    cashbox_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    payment_id uuid,
    updated_by uuid,
    label character varying(300) NOT NULL
);

CREATE TABLE public.cashbox_voucher_details (
    line_amount numeric(12,2),
    quantity numeric(10,2),
    unit_price numeric(12,2),
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    cashbox_voucher_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    item_id uuid,
    updated_by uuid,
    item_name character varying(200)
);

CREATE TABLE public.cashbox_vouchers (
    amount numeric(12,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    cashbox_id uuid,
    created_by uuid,
    expense_category_id uuid,
    id uuid NOT NULL,
    supplier_id uuid,
    updated_by uuid,
    status character varying(20) NOT NULL,
    code character varying(50),
    ticket_file character varying(500),
    description text
);

CREATE TABLE public.cashboxes (
    balance numeric(12,2) NOT NULL,
    opening_balance numeric(12,2) NOT NULL,
    statut integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    type character varying(20) NOT NULL,
    name character varying(100) NOT NULL
);

CREATE TABLE public.category_prestations (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(200) NOT NULL,
    slug character varying(200) NOT NULL
);

CREATE TABLE public.category_tests (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    code character varying(50),
    name character varying(200) NOT NULL
);

CREATE TABLE public.chats (
    is_read boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    receiver_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    updated_by uuid,
    message text NOT NULL
);

CREATE TABLE public.clients (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    adress character varying(255),
    contact character varying(255),
    ifu character varying(255),
    name character varying(255) NOT NULL
);

CREATE TABLE public.consultation_files (
    created_at timestamp(6) without time zone NOT NULL,
    consultation_id uuid NOT NULL,
    id uuid NOT NULL,
    type_file_label character varying(200),
    comment text,
    path text NOT NULL
);

CREATE TABLE public.consultations (
    fees numeric(10,2),
    created_at timestamp(6) without time zone NOT NULL,
    date timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    next_appointment timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    appointment_id uuid,
    attribuate_doctor_id uuid,
    branch_id uuid NOT NULL,
    created_by uuid,
    doctor_id uuid,
    id uuid NOT NULL,
    patient_id uuid NOT NULL,
    prestation_id uuid,
    type_consultation_id uuid,
    updated_by uuid,
    status character varying(20),
    payment_mode character varying(30),
    code character varying(50),
    anamnese text,
    antecedent text,
    diagnostic text,
    examen_physique text,
    motif text,
    notes text
);

CREATE TABLE public.contrats (
    end_date date,
    invoice_unique boolean,
    is_close boolean NOT NULL,
    nbr_tests integer NOT NULL,
    start_date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    client_id uuid,
    created_by uuid,
    hospital_id uuid,
    id uuid NOT NULL,
    updated_by uuid,
    status character varying(20),
    type character varying(50),
    name character varying(200),
    description text
);

CREATE TABLE public.data_codes (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    code character varying(50),
    type character varying(100),
    label character varying(255) NOT NULL
);

CREATE TABLE public.detail_test_orders (
    discount double precision,
    price double precision NOT NULL,
    status boolean,
    total double precision NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    lab_test_id uuid NOT NULL,
    test_order_id uuid NOT NULL,
    test_name character varying(300) NOT NULL
);

CREATE TABLE public.details_contrats (
    amount_after_remise numeric(10,2),
    amount_remise numeric(10,2),
    pourcentage numeric(5,2),
    price numeric(10,2),
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    category_test_id uuid,
    contrat_id uuid NOT NULL,
    id uuid NOT NULL,
    lab_test_id uuid
);

CREATE TABLE public.doc_versions (
    version integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    file_size bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    doc_id uuid NOT NULL,
    id uuid NOT NULL,
    updated_by uuid,
    user_id uuid,
    title character varying(300),
    attachment character varying(500)
);

CREATE TABLE public.docs (
    is_current_version boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    file_size bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    documentation_category_id uuid,
    id uuid NOT NULL,
    role_id uuid,
    updated_by uuid,
    user_id uuid,
    title character varying(300) NOT NULL,
    attachment character varying(500)
);

CREATE TABLE public.doctors (
    commission double precision,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    telephone character varying(20),
    email character varying(100),
    role character varying(100),
    name character varying(200) NOT NULL
);

CREATE TABLE public.documentation_categories (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(200) NOT NULL
);

CREATE TABLE public.employee_contrats (
    end_date date,
    hourly_gross_rate numeric(10,2),
    probation_end_date date,
    salary numeric(10,2) NOT NULL,
    start_date date NOT NULL,
    transport_allowance numeric(10,2),
    weekly_work_hours integer,
    working_days_per_week integer,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    employee_id uuid NOT NULL,
    id uuid NOT NULL,
    bic character varying(20),
    iban character varying(50),
    type character varying(50),
    termination_reason text
);

CREATE TABLE public.employee_documents (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    file_size bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    employee_id uuid NOT NULL,
    id uuid NOT NULL,
    updated_by uuid,
    type character varying(100),
    name character varying(300) NOT NULL,
    file_path character varying(500)
);

CREATE TABLE public.employee_payrolls (
    deductions numeric(10,2) NOT NULL,
    gross_salary numeric(10,2) NOT NULL,
    month integer NOT NULL,
    net_salary numeric(10,2) NOT NULL,
    paid_at date,
    year integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    employee_id uuid NOT NULL,
    id uuid NOT NULL
);

CREATE TABLE public.employee_timeoffs (
    end_date date NOT NULL,
    start_date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    employee_id uuid NOT NULL,
    id uuid NOT NULL,
    status character varying(20) NOT NULL,
    reason text,
    CONSTRAINT employee_timeoffs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE public.employees (
    date_of_birth date,
    hire_date date,
    salary numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    user_id uuid,
    gender character varying(20),
    phone character varying(20),
    city character varying(100),
    cnss_number character varying(100),
    email character varying(100),
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    nationality character varying(100),
    place_of_birth character varying(200),
    "position" character varying(200),
    photo_url character varying(500),
    address character varying(255)
);

CREATE TABLE public.expence_details (
    line_amount numeric(12,2),
    quantity numeric(10,2),
    unit_price numeric(12,2),
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    article_id uuid,
    branch_id uuid NOT NULL,
    created_by uuid,
    expense_id uuid NOT NULL,
    id uuid NOT NULL,
    updated_by uuid,
    article_name character varying(200)
);

CREATE TABLE public.expense_categories (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(200) NOT NULL,
    description text
);

CREATE TABLE public.expenses (
    amount numeric(12,2) NOT NULL,
    date date,
    paid integer NOT NULL,
    quantity numeric(10,2),
    total_amount numeric(12,2),
    unit_price numeric(12,2),
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    cashbox_voucher_id uuid,
    created_by uuid,
    expense_categorie_id uuid,
    id uuid NOT NULL,
    item_id uuid,
    supplier_id uuid,
    updated_by uuid,
    payment character varying(20),
    invoice_number character varying(100),
    item_name character varying(200),
    receipt character varying(500),
    description text
);

CREATE TABLE public.hospitals (
    commission double precision,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    telephone character varying(20),
    email character varying(100),
    name character varying(200) NOT NULL,
    adresse text
);

CREATE TABLE public.invoice_details (
    discount double precision,
    price double precision,
    quantity integer NOT NULL,
    total numeric(10,2) NOT NULL,
    unit_price numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    lab_test_id uuid NOT NULL,
    test_name character varying(100)
);

CREATE TABLE public.invoices (
    date date,
    discount double precision,
    due_date date,
    paid boolean NOT NULL,
    status_invoice integer NOT NULL,
    subtotal double precision,
    total numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    contrat_id uuid,
    created_by uuid,
    id uuid NOT NULL,
    patient_id uuid,
    reference uuid,
    test_order_id uuid,
    updated_by uuid,
    status character varying(20) NOT NULL,
    payment character varying(30),
    code character varying(50),
    date_generate character varying(50),
    client_name character varying(100),
    code_mecef character varying(100),
    code_normalise character varying(100),
    nim character varying(100),
    client_address text,
    counters text,
    qrcode text,
    CONSTRAINT invoices_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'PARTIALLY_PAID'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.lab_tests (
    price numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    status character varying(10) NOT NULL,
    branch_id uuid NOT NULL,
    category_test_id uuid,
    created_by uuid,
    id uuid NOT NULL,
    unit_measurement_id uuid,
    updated_by uuid,
    code character varying(50),
    name character varying(300) NOT NULL,
    normal_value text
);

CREATE TABLE public.log_reports (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    report_id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    action character varying(100) NOT NULL,
    description text
);

CREATE TABLE public.movements (
    movement_date date,
    quantity numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    type character varying(10) NOT NULL,
    article_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    user_id uuid,
    notes text,
    CONSTRAINT movements_type_check CHECK (((type)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying, 'ADJUSTMENT'::character varying])::text[])))
);

CREATE TABLE public.patients (
    age integer,
    birthday date,
    year_or_month boolean,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    genre character varying(20),
    langue character varying(20),
    telephone1 character varying(20),
    telephone2 character varying(20),
    code character varying(100),
    email character varying(100),
    firstname character varying(200) NOT NULL,
    lastname character varying(200) NOT NULL,
    profession character varying(200),
    adresse text
);

CREATE TABLE public.payments (
    amount numeric(10,2) NOT NULL,
    payment_date date NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    updated_by uuid,
    payment_amount character varying(20),
    payment_status character varying(20),
    method character varying(30) NOT NULL,
    payment_name character varying(50),
    payment_id character varying(100),
    description text,
    notes text,
    payment_number text,
    CONSTRAINT payments_method_check CHECK (((method)::text = ANY ((ARRAY['CASH'::character varying, 'CARD'::character varying, 'TRANSFER'::character varying, 'CHECK'::character varying, 'MOBILE_MONEY'::character varying])::text[])))
);

CREATE TABLE public.permissions (
    created_at timestamp(6) without time zone NOT NULL,
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL
);

CREATE TABLE public.prestation_orders (
    total numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    patient_id uuid NOT NULL,
    prestation_id uuid NOT NULL,
    updated_by uuid,
    status character varying(20) NOT NULL
);

CREATE TABLE public.prestations (
    price numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    category_prestation_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(300) NOT NULL,
    description text
);

CREATE TABLE public.problem_categories (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(200) NOT NULL
);

CREATE TABLE public.problem_reports (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    problem_category_id uuid,
    test_order_id uuid NOT NULL,
    updated_by uuid,
    status character varying(20) NOT NULL,
    description text
);

CREATE TABLE public.refund_reasons (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    label character varying(300) NOT NULL
);

CREATE TABLE public.refund_request_logs (
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    id uuid NOT NULL,
    refund_request_id uuid NOT NULL,
    user_id uuid,
    operation character varying(50)
);

CREATE TABLE public.refund_requests (
    montant numeric(10,2) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    invoice_id uuid,
    refund_reason_id uuid,
    updated_by uuid,
    code character varying(50),
    status character varying(50) NOT NULL,
    attachment character varying(500),
    note text
);

CREATE TABLE public.report_tags (
    report_id uuid NOT NULL,
    tag_id uuid NOT NULL
);

CREATE TABLE public.reports (
    is_called boolean NOT NULL,
    is_delivered boolean NOT NULL,
    call_date timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    delivery_date timestamp(6) without time zone,
    signature_date timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    reviewed_by_user_id uuid,
    signatory1 uuid,
    signatory2 uuid,
    signatory3 uuid,
    template_id uuid,
    test_order_id uuid NOT NULL,
    title_id uuid,
    updated_by uuid,
    status character varying(30) NOT NULL,
    code character varying(100),
    receiver_name character varying(200),
    retriever_name character varying(200),
    comment text,
    comment_sup text,
    content text,
    content_micro text,
    description text,
    description_supplementaire text,
    description_supplementaire_micro text,
    receiver_signature text,
    retriever_signature text,
    CONSTRAINT reports_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_REVIEW'::character varying, 'VALIDATED'::character varying, 'DELIVERED'::character varying])::text[])))
);

CREATE TABLE public.revoked_tokens (
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone NOT NULL,
    jti character varying(255) NOT NULL
);

CREATE TABLE public.role_permissions (
    permission_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE public.roles (
    is_assignable boolean,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    description character varying(255),
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL
);

CREATE TABLE public.setting_apps (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    key character varying(100) NOT NULL,
    label character varying(200),
    value text
);

CREATE TABLE public.setting_invoices (
    status boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    ifu character varying(50),
    token text
);

CREATE TABLE public.setting_report_templates (
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    id uuid NOT NULL,
    name character varying(200),
    logo_path character varying(500),
    content text,
    description text,
    detail text,
    footer text,
    header text,
    title character varying(255)
);

CREATE TABLE public.settings (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    ico character varying(100),
    key character varying(100) NOT NULL,
    placeholder character varying(200),
    value text
);

CREATE TABLE public.signals (
    status boolean,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    test_order_id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    type_signal character varying(100),
    commentaire text
);

CREATE TABLE public.supplier_categories (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(200) NOT NULL,
    description text
);

CREATE TABLE public.suppliers (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    supplier_category_id uuid,
    updated_by uuid,
    phone character varying(20),
    category character varying(100),
    email character varying(100),
    name character varying(200) NOT NULL,
    address text,
    information text
);

CREATE TABLE public.tags (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(100) NOT NULL
);

CREATE TABLE public.test_order_assignment_details (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    test_order_assignment_id uuid NOT NULL,
    test_order_id uuid,
    updated_by uuid,
    test_order_code character varying(50),
    note text
);

CREATE TABLE public.test_order_assignments (
    date date,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    code character varying(50),
    note text
);

CREATE TABLE public.test_orders (
    discount double precision,
    is_urgent boolean,
    option boolean,
    prelevement_date date NOT NULL,
    subtotal double precision,
    total double precision,
    assignment_date timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    assigned_to_user_id uuid,
    attribuate_doctor_id uuid,
    branch_id uuid NOT NULL,
    contrat_id uuid,
    created_by uuid,
    doctor_id uuid,
    hospital_id uuid,
    id uuid NOT NULL,
    patient_id uuid NOT NULL,
    type_order_id uuid,
    updated_by uuid,
    status character varying(30) NOT NULL,
    code character varying(50),
    archive character varying(255),
    files_name text,
    reference_hopital character varying(255),
    status_appel character varying(255),
    test_affiliate text,
    CONSTRAINT test_orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'VALIDATED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.test_pathology_macros (
    circulation boolean,
    embedding boolean,
    macro_date date,
    microtomy_spreading boolean,
    mounting boolean,
    staining boolean,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    employee_id uuid,
    id uuid NOT NULL,
    test_order_id uuid,
    updated_by uuid,
    title character varying(300),
    content text,
    observation text
);

CREATE TABLE public.ticket_comments (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    comment text NOT NULL
);

CREATE TABLE public.tickets (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    priority character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    ticket_code character varying(100),
    title character varying(300) NOT NULL,
    description text,
    CONSTRAINT tickets_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT tickets_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[])))
);

CREATE TABLE public.title_reports (
    is_default boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(300) NOT NULL
);

CREATE TABLE public.two_fas (
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    code character varying(255) NOT NULL
);

CREATE TABLE public.type_consultations (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    name character varying(200) NOT NULL,
    slug character varying(200)
);

CREATE TABLE public.type_orders (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    slug character varying(100),
    title character varying(200) NOT NULL
);

CREATE TABLE public.unit_measurements (
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    abbreviation character varying(20),
    name character varying(100) NOT NULL
);

CREATE TABLE public.user_roles (
    role_id uuid NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE public.users (
    commission numeric(10,2),
    email_notification boolean NOT NULL,
    is_active boolean NOT NULL,
    is_connect boolean NOT NULL,
    opt integer,
    two_factor_enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    deleted_at timestamp(6) without time zone,
    reset_token_expires_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    branch_id uuid NOT NULL,
    created_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    phone character varying(20),
    email character varying(150) NOT NULL,
    firstname character varying(200) NOT NULL,
    lastname character varying(200) NOT NULL,
    lastlogindevice character varying(255),
    password character varying(255) NOT NULL,
    reset_token character varying(255),
    signature text,
    two_factor_secret character varying(255),
    whatsapp character varying(255)
);

CREATE TABLE public.users_permissions (
    permission_id uuid NOT NULL,
    user_id uuid NOT NULL
);

ALTER TABLE ONLY public.appel_by_reports
    ADD CONSTRAINT appel_by_reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT articles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.bank_deposits
    ADD CONSTRAINT bank_deposits_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.banks
    ADD CONSTRAINT banks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cashbox_dailies
    ADD CONSTRAINT cashbox_dailies_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cashbox_operations
    ADD CONSTRAINT cashbox_operations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cashbox_tickets
    ADD CONSTRAINT cashbox_tickets_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cashbox_voucher_details
    ADD CONSTRAINT cashbox_voucher_details_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cashbox_vouchers
    ADD CONSTRAINT cashbox_vouchers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cashboxes
    ADD CONSTRAINT cashboxes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.category_prestations
    ADD CONSTRAINT category_prestations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.category_tests
    ADD CONSTRAINT category_tests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT chats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_ifu_key UNIQUE (ifu);

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.consultation_files
    ADD CONSTRAINT consultation_files_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT consultations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.contrats
    ADD CONSTRAINT contrats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.data_codes
    ADD CONSTRAINT data_codes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.detail_test_orders
    ADD CONSTRAINT detail_test_orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.details_contrats
    ADD CONSTRAINT details_contrats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.doc_versions
    ADD CONSTRAINT doc_versions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.docs
    ADD CONSTRAINT docs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.doctors
    ADD CONSTRAINT doctors_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.documentation_categories
    ADD CONSTRAINT documentation_categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_contrats
    ADD CONSTRAINT employee_contrats_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_documents
    ADD CONSTRAINT employee_documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_payrolls
    ADD CONSTRAINT employee_payrolls_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employee_timeoffs
    ADD CONSTRAINT employee_timeoffs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.expence_details
    ADD CONSTRAINT expence_details_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.expense_categories
    ADD CONSTRAINT expense_categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.hospitals
    ADD CONSTRAINT hospitals_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invoice_details
    ADD CONSTRAINT invoice_details_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_test_order_id_key UNIQUE (test_order_id);

ALTER TABLE ONLY public.lab_tests
    ADD CONSTRAINT lab_tests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.log_reports
    ADD CONSTRAINT log_reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT movements_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_code_key UNIQUE (code);

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.prestation_orders
    ADD CONSTRAINT prestation_orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.prestations
    ADD CONSTRAINT prestations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.problem_categories
    ADD CONSTRAINT problem_categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.problem_reports
    ADD CONSTRAINT problem_reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.refund_reasons
    ADD CONSTRAINT refund_reasons_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.refund_request_logs
    ADD CONSTRAINT refund_request_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.refund_requests
    ADD CONSTRAINT refund_requests_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_test_order_id_key UNIQUE (test_order_id);

ALTER TABLE ONLY public.revoked_tokens
    ADD CONSTRAINT revoked_tokens_pkey PRIMARY KEY (jti);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.setting_apps
    ADD CONSTRAINT setting_apps_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.setting_invoices
    ADD CONSTRAINT setting_invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.setting_report_templates
    ADD CONSTRAINT setting_report_templates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.signals
    ADD CONSTRAINT signals_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.supplier_categories
    ADD CONSTRAINT supplier_categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.test_order_assignment_details
    ADD CONSTRAINT test_order_assignment_details_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.test_order_assignments
    ADD CONSTRAINT test_order_assignments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.test_orders
    ADD CONSTRAINT test_orders_code_key UNIQUE (code);

ALTER TABLE ONLY public.test_orders
    ADD CONSTRAINT test_orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.test_pathology_macros
    ADD CONSTRAINT test_pathology_macros_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.test_pathology_macros
    ADD CONSTRAINT test_pathology_macros_test_order_id_key UNIQUE (test_order_id);

ALTER TABLE ONLY public.ticket_comments
    ADD CONSTRAINT ticket_comments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tickets
    ADD CONSTRAINT tickets_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tickets
    ADD CONSTRAINT tickets_ticket_code_key UNIQUE (ticket_code);

ALTER TABLE ONLY public.title_reports
    ADD CONSTRAINT title_reports_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.two_fas
    ADD CONSTRAINT two_fas_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.two_fas
    ADD CONSTRAINT two_fas_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.type_consultations
    ADD CONSTRAINT type_consultations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.type_orders
    ADD CONSTRAINT type_orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.type_orders
    ADD CONSTRAINT type_orders_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.unit_measurements
    ADD CONSTRAINT unit_measurements_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT fk15ennrtsq71w38v51iyryvlts FOREIGN KEY (supplier_category_id) REFERENCES public.supplier_categories(id);

ALTER TABLE ONLY public.contrats
    ADD CONSTRAINT fk24of7524xygim4gqa0u3s93v2 FOREIGN KEY (hospital_id) REFERENCES public.hospitals(id);

ALTER TABLE ONLY public.employee_documents
    ADD CONSTRAINT fk28g0aba9xtbkf6bp9pnvtcw5e FOREIGN KEY (employee_id) REFERENCES public.employees(id);

ALTER TABLE ONLY public.branch_user
    ADD CONSTRAINT fk2vq0h5pmv5cq92l56tqq026o4 FOREIGN KEY (branch_id) REFERENCES public.branches(id);

ALTER TABLE ONLY public.test_order_assignment_details
    ADD CONSTRAINT fk31rq3gfvar00wok7hi9hnc1k FOREIGN KEY (test_order_assignment_id) REFERENCES public.test_order_assignments(id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk3th6bmyclhag7i4ic82i90v9x FOREIGN KEY (title_id) REFERENCES public.title_reports(id);

ALTER TABLE ONLY public.invoice_details
    ADD CONSTRAINT fk439lfpbc6j1k0cn26wtp8f96r FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);

ALTER TABLE ONLY public.details_contrats
    ADD CONSTRAINT fk46nlgvfctylerx731h4c4q653 FOREIGN KEY (lab_test_id) REFERENCES public.lab_tests(id);

ALTER TABLE ONLY public.docs
    ADD CONSTRAINT fk498tbrw9tvgs735les3b2g1pt FOREIGN KEY (documentation_category_id) REFERENCES public.documentation_categories(id);

ALTER TABLE ONLY public.tickets
    ADD CONSTRAINT fk4eqsebpimnjen0q46ja6fl2hl FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.prestation_orders
    ADD CONSTRAINT fk4oq72ywpc8nci7u0ce8osondo FOREIGN KEY (prestation_id) REFERENCES public.prestations(id);

ALTER TABLE ONLY public.users_permissions
    ADD CONSTRAINT fk69cfatgplsb0u6rxkfn14fv5b FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT fk69x3vjuy1t5p18a5llb8h2fjx FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.log_reports
    ADD CONSTRAINT fk6d0dqq4c46vjcjjbuw12333qq FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT fk6dbye15iemw6gjqt0q4q06nf1 FOREIGN KEY (receiver_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.cashbox_dailies
    ADD CONSTRAINT fk6mupa6h9xbkd5kyo1hnytwav FOREIGN KEY (cashbox_id) REFERENCES public.cashboxes(id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT fk7dp82l2vewg53rmbs6oa2k27o FOREIGN KEY (type_consultation_id) REFERENCES public.type_consultations(id);

ALTER TABLE ONLY public.docs
    ADD CONSTRAINT fk7eg0hfub90saxd0b80l3fmsbt FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk7hx1w2wya4gouxdrs34600x4c FOREIGN KEY (reviewed_by_user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.cashbox_vouchers
    ADD CONSTRAINT fk835buthk2qt9khv9nm072yyf4 FOREIGN KEY (cashbox_id) REFERENCES public.cashboxes(id);

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk886ced1atxgvnf1o3oxtj5m4s FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk8exap5wmg8kmb1g1rx3by21yt FOREIGN KEY (patient_id) REFERENCES public.patients(id);

ALTER TABLE ONLY public.lab_tests
    ADD CONSTRAINT fk8r0s7xiqkyjw6gr7179bxwwqc FOREIGN KEY (unit_measurement_id) REFERENCES public.unit_measurements(id);

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk9k54pbr5pted8mj9qte9tpuro FOREIGN KEY (test_order_id) REFERENCES public.test_orders(id);

ALTER TABLE ONLY public.docs
    ADD CONSTRAINT fk9tkihf94m82526acn683ihkdc FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.signals
    ADD CONSTRAINT fkadfp7o1r96a97ft9hblv4xn65 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.bank_deposits
    ADD CONSTRAINT fkai7iulvl1s81ti3vusotl8ubs FOREIGN KEY (bank_id) REFERENCES public.banks(id);

ALTER TABLE ONLY public.detail_test_orders
    ADD CONSTRAINT fkapmfkve3pt5iehxufdlsg7ukr FOREIGN KEY (test_order_id) REFERENCES public.test_orders(id);

ALTER TABLE ONLY public.expence_details
    ADD CONSTRAINT fkaxig1kqdy0nmj8jo1krbn2ufp FOREIGN KEY (expense_id) REFERENCES public.expenses(id);

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fkbicghphnynuqptkktgd63ru9f FOREIGN KEY (contrat_id) REFERENCES public.contrats(id);

ALTER TABLE ONLY public.report_tags
    ADD CONSTRAINT fkbwotfxfisgmb3y2273jgecfia FOREIGN KEY (tag_id) REFERENCES public.tags(id);

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT fkbyer1akkqg2n2jwwjrxfgmjfl FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT fkd8782wnver8s0os2fy050oc4y FOREIGN KEY (expense_categorie_id) REFERENCES public.expense_categories(id);

ALTER TABLE ONLY public.prestation_orders
    ADD CONSTRAINT fkdndt0ma8ihk84swh4yadm5vqp FOREIGN KEY (patient_id) REFERENCES public.patients(id);

ALTER TABLE ONLY public.ticket_comments
    ADD CONSTRAINT fkdoce3fj1osdn71h25dhfs160v FOREIGN KEY (ticket_id) REFERENCES public.tickets(id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT fkdqyibd6w1h5h66xn9aqx7fwv5 FOREIGN KEY (patient_id) REFERENCES public.patients(id);

ALTER TABLE ONLY public.employee_contrats
    ADD CONSTRAINT fkdttcfrt32rsyy9ubllslowpta FOREIGN KEY (employee_id) REFERENCES public.employees(id);

ALTER TABLE ONLY public.movements
    ADD CONSTRAINT fkdtum5beih2bxppexqvqrajr7u FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES public.permissions(id);

ALTER TABLE ONLY public.cashbox_tickets
    ADD CONSTRAINT fkf22khtm6vnypc9juej7a1poxj FOREIGN KEY (payment_id) REFERENCES public.payments(id);

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fkg9or6syp6ugtv7gwaec4pxg8f FOREIGN KEY (reference) REFERENCES public.invoices(id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.details_contrats
    ADD CONSTRAINT fkhurx2rg4hmm7oauyab4mvcpxl FOREIGN KEY (contrat_id) REFERENCES public.contrats(id);

ALTER TABLE ONLY public.report_tags
    ADD CONSTRAINT fkida8t7ysiwsl99eo83fy49igl FOREIGN KEY (report_id) REFERENCES public.reports(id);

ALTER TABLE ONLY public.consultation_files
    ADD CONSTRAINT fkix0shv6fidogwxlmvar82m1r6 FOREIGN KEY (consultation_id) REFERENCES public.consultations(id);

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT fkj43l6d4ltvlm605sepdx49t0r FOREIGN KEY (cashbox_voucher_id) REFERENCES public.cashbox_vouchers(id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT fkjmv9dug68njljwof0eep0jrm FOREIGN KEY (doctor_id) REFERENCES public.doctors(id);

ALTER TABLE ONLY public.refund_requests
    ADD CONSTRAINT fkjpi9vjt3yfr2tlr8mbr2ovxme FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);

ALTER TABLE ONLY public.lab_tests
    ADD CONSTRAINT fkjq90wnkwj8nos81tjefv2rvce FOREIGN KEY (category_test_id) REFERENCES public.category_tests(id);

ALTER TABLE ONLY public.cashbox_tickets
    ADD CONSTRAINT fkjvxg0vw520ik4aww0nklsgr14 FOREIGN KEY (cashbox_id) REFERENCES public.cashboxes(id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fkk5tihyjln2thlfwsufh10fatn FOREIGN KEY (signatory2) REFERENCES public.users(id);

ALTER TABLE ONLY public.signals
    ADD CONSTRAINT fkkuk6xi4snq3ltghw4xugmqlpm FOREIGN KEY (test_order_id) REFERENCES public.test_orders(id);

ALTER TABLE ONLY public.employee_timeoffs
    ADD CONSTRAINT fkkvgmej1fumm6bv1cx6daq1mef FOREIGN KEY (employee_id) REFERENCES public.employees(id);

ALTER TABLE ONLY public.doc_versions
    ADD CONSTRAINT fkkyflkgkgh16apretxtxsy6ykt FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.chats
    ADD CONSTRAINT fkla7peq6fislsxok7a4wxv5p36 FOREIGN KEY (sender_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.test_order_assignments
    ADD CONSTRAINT fklw4mlocgj8apoi273yr6slvv FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.branch_user
    ADD CONSTRAINT fkmlgnse0ibk36jgp4yk0dd5ts7 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.log_reports
    ADD CONSTRAINT fkmx353toqvr2i4q2kugbe1fb8y FOREIGN KEY (report_id) REFERENCES public.reports(id);

ALTER TABLE ONLY public.employee_payrolls
    ADD CONSTRAINT fkn4na5wml0daphjgarggns0sjg FOREIGN KEY (employee_id) REFERENCES public.employees(id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fknfyniipu5gmn9hb1h1n8aqlih FOREIGN KEY (signatory3) REFERENCES public.users(id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT fknv8yag7p57dlf8apa2b7518va FOREIGN KEY (attribuate_doctor_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fknwdm5jusvovyak07lmdi027vd FOREIGN KEY (signatory1) REFERENCES public.users(id);

ALTER TABLE ONLY public.invoice_details
    ADD CONSTRAINT fko68op3v6lw1vnuvy63eq0ty7d FOREIGN KEY (lab_test_id) REFERENCES public.lab_tests(id);

ALTER TABLE ONLY public.appel_by_reports
    ADD CONSTRAINT fko7dqvsq8nunlokrc1mt1ry3m1 FOREIGN KEY (report_id) REFERENCES public.reports(id);

ALTER TABLE ONLY public.doc_versions
    ADD CONSTRAINT fkoyxt8oiaj7e6p3nhl0aqre9v FOREIGN KEY (doc_id) REFERENCES public.docs(id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT fkp77tpwkqp4e3fxdi9d7eo44cx FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);

ALTER TABLE ONLY public.prestations
    ADD CONSTRAINT fkpjmq0f7e699i4fe1n3hq2hhk FOREIGN KEY (category_prestation_id) REFERENCES public.category_prestations(id);

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fkq8wy8lruw5js0ugr33f8pwj51 FOREIGN KEY (test_order_id) REFERENCES public.test_orders(id);

ALTER TABLE ONLY public.ticket_comments
    ADD CONSTRAINT fkqstmdduoeqr1bm2lj8r5tmhl2 FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.consultations
    ADD CONSTRAINT fkqtotrgvovkkb9uwl3pm7878jn FOREIGN KEY (prestation_id) REFERENCES public.prestations(id);

ALTER TABLE ONLY public.users_permissions
    ADD CONSTRAINT fkratfh9vckun1eq8dktpbmdbsj FOREIGN KEY (permission_id) REFERENCES public.permissions(id);

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fkrbqec6be74wab8iifh8g3i50i FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fkrpyotno5h237hyoaokuggqqog FOREIGN KEY (patient_id) REFERENCES public.patients(id);

ALTER TABLE ONLY public.cashbox_voucher_details
    ADD CONSTRAINT fksnrvcqo82ugxi82jpkcvmxjf6 FOREIGN KEY (cashbox_voucher_id) REFERENCES public.cashbox_vouchers(id);

ALTER TABLE ONLY public.cashbox_operations
    ADD CONSTRAINT fksthw7kh8vfe0mph3yuair1j0q FOREIGN KEY (cashbox_id) REFERENCES public.cashboxes(id);

ALTER TABLE ONLY public.bank_deposits
    ADD CONSTRAINT fksvrae1hn7tx9kf4xppr5s7yy3 FOREIGN KEY (cashbox_id) REFERENCES public.cashboxes(id);

ALTER TABLE ONLY public.refund_request_logs
    ADD CONSTRAINT fkt6ky44tc9v94vm3fxfl1s6ssk FOREIGN KEY (refund_request_id) REFERENCES public.refund_requests(id);

ALTER TABLE ONLY public.problem_reports
    ADD CONSTRAINT fkt8872bshm5u7flljijhhllnb2 FOREIGN KEY (test_order_id) REFERENCES public.test_orders(id);

ALTER TABLE ONLY public.test_order_assignment_details
    ADD CONSTRAINT fkujvv0tb93u6iimqloxio0ocn FOREIGN KEY (test_order_id) REFERENCES public.test_orders(id);

ALTER TABLE ONLY public.problem_reports
    ADD CONSTRAINT fkwv6mhges0c9xbmnfqx6yiiag FOREIGN KEY (problem_category_id) REFERENCES public.problem_categories(id);

