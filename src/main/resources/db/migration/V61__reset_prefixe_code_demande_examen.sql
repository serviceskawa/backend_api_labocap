-- V61 — Remise au format Laravel du code de demande d'examen
--
-- Format de référence Laravel (helpers.php::generateCodeExamen) :
--     {prefixe_code_demande_examen}{aa}-{séquence sur 4}      ex. « 26-0001 »
-- Le préfixe par défaut est VIDE (cf. SettingAppMissingKeysSeeder de Laravel et
-- les codes historiques « 25-3090 », « 25-3091 »), et il est lu dans `setting_apps`.
--
-- Deux écarts constatés sur l'environnement déployé, qui produisaient des codes
-- du type « ABCD26-0001 » :
--   1. le backend lisait le préfixe dans `settings` au lieu de `setting_apps`
--      (corrigé dans TestOrderServiceImpl) ;
--   2. la clé portait une valeur de test non conforme.
--
-- On remet donc la valeur par défaut (chaîne vide) dans `setting_apps` et on
-- supprime la clé parasite de `settings`, qui n'est plus lue par personne et
-- masquerait la valeur de `setting_apps` sur l'écran Paramètres.
-- Les codes déjà générés ne sont pas renommés : ils sont référencés par les
-- comptes rendus (« CO » + code), les factures et les fichiers déjà stockés.

UPDATE setting_apps
   SET value = '',
       updated_at = NOW()
 WHERE key = 'prefixe_code_demande_examen'
   AND COALESCE(value, '') <> '';

-- Crée la clé (valeur vide) pour les branches qui ne l'ont pas encore, afin que
-- l'écran Paramètres l'affiche et puisse l'éditer.
INSERT INTO setting_apps (id, branch_id, key, value, created_at, updated_at)
SELECT gen_random_uuid(), b.id, 'prefixe_code_demande_examen', '', NOW(), NOW()
  FROM branches b
 WHERE NOT EXISTS (
        SELECT 1 FROM setting_apps s
         WHERE s.key = 'prefixe_code_demande_examen'
           AND s.branch_id = b.id
       );

DELETE FROM settings WHERE key = 'prefixe_code_demande_examen';
