-- Crée un agent, ou remet à jour celui qui porte déjà ce courriel.
--
-- Usage, depuis le répertoire du projet :
--
--   docker compose exec -T db psql -U postgres -d labo_anapath \
--     -v prenom="Awa" -v nom="TESTEUSE" \
--     -v courriel="awa.testeuse@caap.bj" \
--     -v motdepasse="UnMotDePasseSolide!" \
--     -v pin="4417" \
--     -v branche="MDM CENTER" \
--     -v role="Laborantin" \
--     < scripts/creer_agent.sql
--
-- Les deux secrets sont hachés par la base (pgcrypto, BCrypt coût 10) : ils ne
-- transitent donc jamais en clair ailleurs que dans cette commande, et le
-- format est exactement celui que produit Spring Security — « $2a$10$… ».
--
-- Relançable sans dommage : trois exécutions laissent un agent, un rôle, un
-- accès.

\set ON_ERROR_STOP on
BEGIN;

-- 1. L'agent lui-même.
INSERT INTO users (
  id, branch_id, email, firstname, lastname,
  password, pin_hash,
  is_active, is_connect, email_notification, two_factor_enabled,
  created_at, updated_at)
SELECT gen_random_uuid(), b.id, lower(:'courriel'), :'prenom', :'nom',
       crypt(:'motdepasse', gen_salt('bf', 10)),
       crypt(:'pin',        gen_salt('bf', 10)),
       true, false, false, false,
       now(), now()
FROM branches b
WHERE b.name = :'branche' OR b.id::text = :'branche'
LIMIT 1
ON CONFLICT (email) DO UPDATE SET
  -- On remet l'agent en état connu plutôt que d'échouer : la commande sert
  -- aussi à réparer un compte, et refuser l'aurait rendue inutilisable là où
  -- elle est le plus utile.
  firstname  = EXCLUDED.firstname,
  lastname   = EXCLUDED.lastname,
  password   = EXCLUDED.password,
  pin_hash   = EXCLUDED.pin_hash,
  branch_id  = EXCLUDED.branch_id,
  is_active  = true,
  deleted_at = NULL,
  pin_failed_attempts = 0,
  pin_locked_until    = NULL,
  updated_at = now();

-- 2. Le rôle.
--
-- `ON CONFLICT` ne mordrait pas : la table n'a aucune contrainte d'unicité sur
-- (user_id, role_id). Relancer ajouterait donc une seconde liaison au même
-- rôle — invisible à l'écran, mais qui fausse tout décompte par rôle.
INSERT INTO user_roles (user_id, role_id, created_at, updated_at)
SELECT u.id, r.id, now(), now()
FROM users u, roles r
WHERE u.email = lower(:'courriel')
  AND r.name = :'role'
  AND NOT EXISTS (SELECT 1 FROM user_roles x
                  WHERE x.user_id = u.id AND x.role_id = r.id);

-- 3. L'ACCÈS à la branche, qui n'est pas la branche d'attache.
--
-- `users.branch_id` dit de quelle branche l'agent dépend ; `branch_user` dit à
-- laquelle il a le droit d'accéder. Le filtre de branche interroge la seconde,
-- la connexion mobile renvoie la première. Sans cette ligne, la session s'ouvre
-- normalement puis chaque appel est refusé — « votre accès à cette branche a
-- été révoqué, veuillez en sélectionner une autre » — et l'application mobile
-- invite alors à choisir une autre branche, ce qu'elle ne sait pas faire.
-- L'agent est bloqué sans aucun recours depuis son téléphone.
INSERT INTO branch_user (user_id, branch_id, is_default, created_at, updated_at)
SELECT u.id, u.branch_id, true, now(), now()
FROM users u
WHERE u.email = lower(:'courriel')
  AND NOT EXISTS (SELECT 1 FROM branch_user bu
                  WHERE bu.user_id = u.id AND bu.branch_id = u.branch_id
                    AND bu.deleted_at IS NULL);

COMMIT;

-- Ce qu'on a obtenu, pour le lire d'un coup d'œil.
SELECT u.firstname, u.lastname, u.email,
       b.name AS branche,
       string_agg(DISTINCT r.name, ', ') AS roles,
       u.is_active,
       (u.pin_hash IS NOT NULL) AS pin_pose,
       (SELECT count(*) FROM branch_user bu
        WHERE bu.user_id = u.id AND bu.deleted_at IS NULL) AS acces_branche
FROM users u
JOIN branches b    ON b.id = u.branch_id
LEFT JOIN user_roles ur ON ur.user_id = u.id
LEFT JOIN roles r       ON r.id = ur.role_id
WHERE u.email = lower(:'courriel')
GROUP BY u.firstname, u.lastname, u.email, b.name, u.is_active, u.pin_hash, u.id;
