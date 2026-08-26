-- Deux patients peuvent partager un numéro de téléphone.
--
-- L'index unique `(telephone1, branch_id)`, hérité de la reprise Laravel,
-- traitait le numéro comme une identité. C'est faux dans un laboratoire :
-- une mère donne son numéro pour ses trois enfants, un mari pour son épouse,
-- un service hospitalier pour tous les prélèvements qu'il envoie. Le refus
-- tombait alors au comptoir, devant quelqu'un qui attendait, et l'agent
-- s'en tirait en inventant un chiffre — ce qui abîme la donnée bien plus
-- sûrement qu'un doublon.
--
-- L'identité d'un patient reste son `code`, dont l'unicité ne bouge pas.
--
-- L'index simple sur `telephone1` demeure : chercher un patient par son
-- numéro est un geste courant au comptoir, et c'est lui qui le sert. Seule
-- l'unicité disparaît.

DROP INDEX IF EXISTS idx_patients_telephone1_branch;
