-- À quel titre le compte rendu a été emporté.
--
-- POURQUOI CETTE COLONNE
-- Un compte rendu d'anatomie pathologique est une donnée de santé, et le
-- remettre à quelqu'un qui n'est pas le patient est une divulgation à un tiers.
-- La trace actuelle dit « Marie TOWOU a emporté le dossier » ; elle ne dit pas
-- à quel titre, donc elle ne dit pas si cette remise était légitime. Le jour où
-- une famille conteste qu'un résultat a été communiqué à un proche, le nom seul
-- ne répond pas. Cette colonne est ce qui transforme un nom en justification.
--
-- POURQUOI DU TEXTE LIBRE
-- Une liste fermée serait plus facile à compter, mais le comptoir voit des cas
-- qu'aucune liste ne prévoit — un voisin, un employeur, un service hospitalier.
-- Contraint de choisir une case fausse, l'agent abîmerait la trace au lieu de
-- la servir. On préfère une donnée fidèle et malcommode à une donnée propre et
-- fausse.
--
-- POURQUOI ELLE RESTE NULLE POUR L'EXISTANT
-- Les remises déjà enregistrées n'ont pas de qualité, et aucune reprise n'est
-- possible : on ne devine pas après coup à quel titre quelqu'un est reparti.
-- Elles s'afficheront « Emporté par X », sans parenthèse — un vide honnête.

ALTER TABLE reports ADD COLUMN IF NOT EXISTS retriever_relation VARCHAR(120);

COMMENT ON COLUMN reports.retriever_relation IS
    'Qualité de la personne ayant emporté le compte rendu : « Lui-même », « Mère », « Coursier »… Texte libre, nul pour les remises antérieures à sa création.';
