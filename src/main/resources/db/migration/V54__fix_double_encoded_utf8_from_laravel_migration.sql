-- V54: Réparation du double encodage UTF-8 hérité de la migration Laravel/MySQL
--
-- Lors de la reprise des données depuis l'ancienne base Laravel (MySQL latin1),
-- des textes déjà encodés en UTF-8 ont été relus comme du Latin-1/Windows-1252
-- puis ré-encodés en UTF-8 : un « double encodage ». Résultat, « Pièce
-- opératoire » est stocké « PiÃ¨ce opÃ©ratoire », « français » devient
-- « franÃ§ais », l'apostrophe typographique « ' » devient « â€™ », « œ »
-- devient « Å" », etc. L'API et le frontend affichent fidèlement ces octets :
-- le défaut est UNIQUEMENT dans la donnée stockée, pas dans le code.
--
-- Cette migration inverse l'opération : elle réencode le texte en Windows-1252
-- (superset de Latin-1 qui couvre €, œ, guillemets courbes…) puis le relit en
-- UTF-8, ce qui restitue les caractères d'origine.
--
-- Robustesse (fonction _fix_mojibake ci-dessous) :
--   1. On tente d'abord WIN1252 (gère €, œ, apostrophes courbes = octets 0x80-0x9F).
--   2. En cas d'échec (octet sans équivalent WIN1252, ex. 0x8F), on retente en
--      LATIN1 (couvre tout l'intervalle 0x00-0xFF).
--   3. Si les deux échouent, ou si le résultat n'est pas de l'UTF-8 valide, on
--      renvoie la valeur inchangée. La migration ne peut donc JAMAIS avorter ni
--      aggraver une valeur.
--
-- Auto-protection : une chaîne réellement correcte (ex. « Âge ») ne forme pas
-- une séquence UTF-8 valide une fois réencodée, la conversion échoue et la
-- valeur est laissée telle quelle. Seul le vrai mojibake est corrigé.
--
-- Idempotent : le filtre ne retient que les valeurs porteuses des marqueurs de
-- mojibake (Ã, Â, Å, â€). Une valeur réparée ne les contient plus et n'est
-- donc jamais retouchée à un rechargement de dump.

-- Fonction de réparation best-effort, à portée de la migration uniquement.
CREATE OR REPLACE FUNCTION _fix_mojibake(t text) RETURNS text AS $func$
BEGIN
    IF t IS NULL OR t = '' THEN
        RETURN t;
    END IF;
    -- Tentative principale : Windows-1252 (couvre 0x80-0x9F : €, œ, « ' », …).
    BEGIN
        RETURN convert_from(convert_to(t, 'WIN1252'), 'UTF8');
    EXCEPTION WHEN others THEN
        -- Repli : Latin-1 (ISO-8859-1) couvre tout 0x00-0xFF.
        BEGIN
            RETURN convert_from(convert_to(t, 'LATIN1'), 'UTF8');
        EXCEPTION WHEN others THEN
            -- Irrécupérable proprement : on ne touche pas.
            RETURN t;
        END;
    END;
END;
$func$ LANGUAGE plpgsql;

-- Parcours de toutes les colonnes texte du schéma public et réparation des
-- lignes porteuses d'un marqueur de mojibake.
DO $$
DECLARE
    r          record;
    updated    bigint;
    total      bigint := 0;
BEGIN
    FOR r IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND data_type IN ('character varying', 'text', 'character')
        ORDER BY table_name, column_name
    LOOP
        EXECUTE format(
            $sql$
                UPDATE public.%1$I
                SET %2$I = _fix_mojibake(%2$I)
                WHERE (%2$I LIKE '%%Ã%%'
                    OR %2$I LIKE '%%Â%%'
                    OR %2$I LIKE '%%Å%%'
                    OR %2$I LIKE '%%â€%%')
                  AND _fix_mojibake(%2$I) IS DISTINCT FROM %2$I
            $sql$,
            r.table_name, r.column_name
        );
        GET DIAGNOSTICS updated = ROW_COUNT;
        IF updated > 0 THEN
            total := total + updated;
            RAISE NOTICE 'Réparé %.% : % ligne(s)', r.table_name, r.column_name, updated;
        END IF;
    END LOOP;
    RAISE NOTICE 'Total réparé : % valeur(s)', total;
END $$;

DROP FUNCTION _fix_mojibake(text);
