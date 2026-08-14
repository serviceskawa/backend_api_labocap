-- Séparation des droits « valider » et « remettre » un compte-rendu.
--
-- CONTEXTE
-- Jusqu'ici, cinq opérations du ReportController — validate, deliver,
-- delivered-patient, informed-patient et store-signature — étaient toutes
-- gardées par la seule permission `edit-reports`. Autrement dit, quiconque
-- pouvait corriger un compte-rendu pouvait aussi le VALIDER médicalement,
-- c'est-à-dire engager un diagnostic. Les permissions `validate-reports` et
-- `deliver-reports` existaient pourtant depuis V2, mais n'étaient référencées
-- nulle part dans le code Java.
--
-- L'application mobile a besoin de cette distinction : un agent d'accueil qui
-- recueille la signature d'un patient au retrait ne doit pas se voir proposer
-- la validation d'un diagnostic. Le menu mobile est piloté par ces permissions.
--
-- CE QUE FAIT CETTE MIGRATION
-- Elle n'ajoute aucune permission — les deux existent déjà. Elle les accorde à
-- tout rôle qui détient `edit-reports` aujourd'hui.
--
-- POURQUOI CET ÉLARGISSEMENT D'ABORD
-- Le code va commencer à exiger ces permissions. Sans cette reprise, tout rôle
-- qui valide aujourd'hui perdrait ce droit à la seconde du déploiement — les
-- pathologistes en tête. On reconduit donc à l'identique les capacités
-- existantes : cette migration ne retire rien et n'ouvre rien de nouveau.
--
-- LE RESSERREMENT EST UNE DÉCISION MÉTIER, PAS UNE DEVINETTE
-- Retirer `validate-reports` aux rôles d'accueil relève du laboratoire, qui
-- seul sait qui valide. Cela se fait dans l'écran d'administration des rôles,
-- après déploiement. Une migration ne peut pas deviner l'organigramme.
--
-- Les permissions se résolvent par slug seul, comme le fait `@PreAuthorize` à
-- l'exécution et comme le mappe l'entité Permission (id, name, slug, created_at).

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, cible.id
FROM role_permissions rp
JOIN permissions source
       ON source.id = rp.permission_id
      AND source.slug = 'edit-reports'
JOIN permissions cible
       ON cible.slug IN ('validate-reports', 'deliver-reports')
WHERE NOT EXISTS (
    SELECT 1
    FROM role_permissions deja
    WHERE deja.role_id = rp.role_id
      AND deja.permission_id = cible.id
);
