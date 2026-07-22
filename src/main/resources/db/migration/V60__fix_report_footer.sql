-- Corrige le pied de page du compte rendu (report_footer).
--
-- Contexte : la donnée migrée depuis Laravel contenait deux anomalies —
--   1. setting_apps.report_footer se terminait par un suffixe parasite
--      « ssage de texte » (fragment de « message de texte » collé après
--      « www.caap.bj »), imprimé tel quel sur les PDF.
--   2. La table `settings` (store) portait une valeur périmée
--      « Voici un message de texte » qui, lors de la fusion côté frontend
--      ({ ...apps, ...store }), écrasait l'affichage du champ.
--
-- Après cette migration, setting_apps est la source unique du pied de page :
--   * sa valeur est réécrite avec le libellé officiel CAAP ;
--   * la clé report_footer est retirée du store pour ne plus écraser l'affichage
--     ni faire « disparaître » les futures modifications de l'utilisateur.

UPDATE setting_apps
SET value = 'Centre ADECHINA Anatomie Pathologique • Adresse : Carre 1915 "G" Fifadji, 072 BP 059 Cotonou, Bénin • Téléphone : (+229) 97761721 • WhatsApp: (+229)61191975 • RCCM RB/COT/18 B22364 • IFU : 3201810410828 • contact@caap.bj • Ouvert du Lundi au Vendredi de 08:00 - 17:00 • www.caap.bj',
    updated_at = now()
WHERE key = 'report_footer';

DELETE FROM settings WHERE key = 'report_footer';
