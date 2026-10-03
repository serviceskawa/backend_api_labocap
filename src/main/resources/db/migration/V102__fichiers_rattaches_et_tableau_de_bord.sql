-- Contrôle d'accès par ressource (N-05, N-11).
--
-- 1. FICHIERS RATTACHÉS À UNE ENTITÉ
-- Jusqu'ici /api/v1/files/** servait n'importe quel fichier à n'importe quelle
-- personne connectée qui en connaissait le chemin. Chaque fichier est désormais
-- rattaché à l'entité qui le possède (`stored_files`), et le serveur applique,
-- avant de le servir, la permission de lecture de cette entité et l'agence.
--
-- L'identifiant d'un fichier est DÉDUIT de son chemin (UUID v3 = MD5 du
-- chemin, comme `UUID.nameUUIDFromBytes` en Java) : le code calcule le même
-- identifiant que cette migration sans consulter la table, et un DTO peut
-- exposer `fileId` à côté du chemin sans requête supplémentaire.
--
-- L'existant est repris ici, colonne par colonne. Les chemins anciens sont
-- normalisés (préfixes « /api/v1/files/ », « storage/ » ou « / » retirés) pour
-- correspondre à ce que les services de stockage renvoient aujourd'hui.
-- Un chemin qui n'est pas repris ici (orphelin, URL absolue, liste JSON
-- illisible) ne sera plus servi : le bloc final en compte les cas.
--
-- 2. TABLEAU DE BORD
-- Treize routes sans permission. `view-dashboard` protège le contrôleur,
-- `view-dashboard-finance` le chiffre d'affaires et l'état des factures.
-- `view-dashboard` va aussi à tout rôle qui a `view-reports` : sans ce report,
-- tout le monde perdrait son tableau de bord au déploiement.

-- ---------------------------------------------------------------------------
-- 1.a La table.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stored_files (
    id          UUID PRIMARY KEY,
    path        TEXT NOT NULL UNIQUE,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   UUID NOT NULL,
    branch_id   UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_stored_files_entite ON stored_files (entity_type, entity_id);

-- ---------------------------------------------------------------------------
-- 1.b Reprise de l'existant.
-- ---------------------------------------------------------------------------
WITH chemins (chemin, entity_type, entity_id, branch_id) AS (
    -- Clichés d'examen : liste JSON de chemins (`["examen_images/x.jpg", …]`).
    -- Le filtre régulier écarte une liste illisible, qui ferait échouer le cast.
    SELECT f.chemin, 'TEST_ORDER', t.id, t.branch_id
    FROM test_orders t
    CROSS JOIN LATERAL json_array_elements_text(t.files_name::json) AS f(chemin)
    WHERE t.files_name ~ '^\s*\[\s*("([^"\\]|\\.)*"\s*(,\s*"([^"\\]|\\.)*"\s*)*)?\]\s*$'
    UNION ALL
    SELECT t.archive, 'TEST_ORDER', t.id, t.branch_id
    FROM test_orders t
    UNION ALL
    -- Photos et notes vocales du fil de discussion d'une demande.
    SELECT m.content, 'DISCUSSION_MESSAGE', m.id, COALESCE(d.branch_id, t.branch_id)
    FROM discussion_messages m
    JOIN discussions d ON d.id = m.discussion_id
    LEFT JOIN test_orders t ON t.id = d.test_order_id
    WHERE m.type <> 'texte'
    UNION ALL
    SELECT cf.path, 'CONSULTATION_FILE', cf.id, c.branch_id
    FROM consultation_files cf
    JOIN consultations c ON c.id = cf.consultation_id
    UNION ALL
    SELECT e.photo_url, 'EMPLOYEE', e.id, e.branch_id FROM employees e
    UNION ALL
    SELECT d.file_path, 'EMPLOYEE_DOCUMENT', d.id, d.branch_id FROM employee_documents d
    UNION ALL
    SELECT d.attachment, 'DOC', d.id, d.branch_id FROM docs d
    UNION ALL
    SELECT v.attachment, 'DOC_VERSION', v.id, v.branch_id FROM doc_versions v
    UNION ALL
    SELECT e.receipt, 'EXPENSE', e.id, e.branch_id FROM expenses e
    UNION ALL
    SELECT r.attachment, 'REFUND_REQUEST', r.id, r.branch_id FROM refund_requests r
    UNION ALL
    SELECT b.attachement, 'BANK_DEPOSIT', b.id, b.branch_id FROM bank_deposits b
    UNION ALL
    SELECT v.ticket_file, 'CASHBOX_VOUCHER', v.id, v.branch_id FROM cashbox_vouchers v
),
normalises AS (
    SELECT regexp_replace(btrim(chemin), '^(/api/v1/files/|/?storage/|/)+', '') AS path,
           entity_type, entity_id, branch_id
    FROM chemins
    WHERE chemin IS NOT NULL
      AND btrim(chemin) <> ''
      AND btrim(chemin) !~ '^https?://'
      AND branch_id IS NOT NULL
),
identifies AS (
    -- UUID v3 : MD5 du chemin, quartet de version à 3, variante à 10xx —
    -- exactement ce que fait UUID.nameUUIDFromBytes côté Java.
    SELECT (substr(h, 1, 12) || '3' || substr(h, 14, 3)
            || substr('89ab', ((get_byte(decode(h, 'hex'), 8) >> 4) & 3) + 1, 1)
            || substr(h, 18))::uuid AS id,
           n.path, n.entity_type, n.entity_id, n.branch_id
    FROM normalises n
    CROSS JOIN LATERAL (SELECT md5(n.path) AS h) m
    WHERE n.path <> ''
)
INSERT INTO stored_files (id, path, entity_type, entity_id, branch_id, created_at)
-- Un même fichier peut être cité deux fois (le document et sa première
-- version) : la première citation, dans l'ordre des types, l'emporte.
SELECT DISTINCT ON (id) id, path, entity_type, entity_id, branch_id, NOW()
FROM identifies
ORDER BY id, entity_type
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2.a Les deux permissions.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (id, name, slug, created_at, updated_at)
SELECT gen_random_uuid(), v.name, v.slug, NOW(), NOW()
FROM (VALUES
        ('Voir le tableau de bord',              'view-dashboard'),
        ('Voir les montants du tableau de bord', 'view-dashboard-finance')
     ) AS v(name, slug)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.slug = v.slug);

-- ---------------------------------------------------------------------------
-- 2.b Super-admin et admin : les deux.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug IN ('super-admin', 'admin')
  AND p.slug IN ('view-dashboard', 'view-dashboard-finance')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions deja
      WHERE deja.role_id = r.id AND deja.permission_id = p.id
  );

-- ---------------------------------------------------------------------------
-- 2.c Qui lit les comptes rendus garde son tableau de bord.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, cible.id
FROM role_permissions rp
JOIN permissions source ON source.id = rp.permission_id AND source.slug = 'view-reports'
JOIN permissions cible  ON cible.slug = 'view-dashboard'
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions deja
    WHERE deja.role_id = rp.role_id AND deja.permission_id = cible.id
);

-- ---------------------------------------------------------------------------
-- 3. Garde-fou (voir V78, V98) et bilan de la reprise des fichiers.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    s TEXT;
    n_fichiers BIGINT;
    n_listes_illisibles BIGINT;
BEGIN
    FOREACH s IN ARRAY ARRAY['view-dashboard', 'view-dashboard-finance'] LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE slug = s) THEN
            RAISE EXCEPTION 'La permission % est absente.', s;
        END IF;
        IF EXISTS (SELECT 1 FROM roles WHERE slug = 'super-admin') AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN roles r ON r.id = rp.role_id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE r.slug = 'super-admin' AND p.slug = s
        ) THEN
            RAISE EXCEPTION 'Le rôle super-admin n''a pas reçu %.', s;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO n_fichiers FROM stored_files;
    SELECT COUNT(*) INTO n_listes_illisibles
    FROM test_orders
    WHERE files_name IS NOT NULL AND btrim(files_name) <> ''
      AND files_name !~ '^\s*\[\s*("([^"\\]|\\.)*"\s*(,\s*"([^"\\]|\\.)*"\s*)*)?\]\s*$';
    RAISE NOTICE 'stored_files : % fichiers rattachés ; % demandes dont la liste de clichés n''a pas pu être lue.',
        n_fichiers, n_listes_illisibles;
END $$;
