# Reprise de la plateforme Laravel — journal de bord

État au 12 août 2026. Ce document recense ce qui a été migré, les défauts trouvés
et corrigés depuis la bascule en production, et ce qui reste ouvert.

Il complète deux documents existants, qu'il ne répète pas :

- [`ecarts-schema-laravel.md`](ecarts-schema-laravel.md) — comparaison table à table
  entre le dump Laravel et le schéma PostgreSQL ;
- `migration-laravel/analyse/correspondance.md` — la table de correspondance
  utilisée par le convertisseur.

---

## 1. Ce qui a été migré

La base de production MariaDB 10.5 a été convertie vers PostgreSQL 16. Le
convertisseur (`migration-laravel/travail/convertir.py`) applique une table de
correspondance explicite : tables écartées, tables renommées, colonnes renommées,
valeurs posées, réglages éclatés.

Trois principes ont guidé le travail, et méritent d'être retenus.

**L'artefact est réduit à ce que le backend implémente.** Les tables qu'aucune
entité JPA ne lit ne sont pas dans le dump final. Le schéma PostgreSQL est le
contrat : `ddl-auto: validate` le vérifie à chaque démarrage, et une divergence
empêche l'application de démarrer plutôt que de la laisser mentir.

**Le dump a été adapté au backend, non l'inverse.** La décision a été prise après
avoir envisagé le contraire. Renommer les colonnes de Spring pour coller à
Laravel aurait touché des dizaines de fichiers et introduit des régressions dans
des flux déjà éprouvés.

**Chaque chargement est vérifié par des agrégats métier**, pas par un compte de
lignes. Un `ON CONFLICT DO NOTHING` compte les échecs comme des insertions : le
compteur ment. Trois totaux critiques ont servi de juges.

### Pièges rencontrés pendant la migration

| Piège | Conséquence évitée |
|---|---|
| `ON CONFLICT DO NOTHING` gonflant le compteur | 176 références brisées passées inaperçues |
| Table d'identifiants pointant des lignes non insérées | Balayage d'intégrité ajouté après chargement |
| Un téléphone de 121 caractères | Un dossier patient entier perdu ; troncature journalisée depuis |
| `NULL` explicite écrasant un `DEFAULT` | Colonne omise plutôt que forcée à NULL |
| `reports.description_micro` classée « ambiguë » | 18 996 lignes à deux doigts d'être abandonnées |
| `permissions.slug` vidée | Spring a `name` **et** `slug` ; c'est `titre` → `name` |

---

## 2. Les défauts corrigés depuis la bascule

48 commits au total — 24 de chaque côté. Plutôt que de les énumérer, ils sont
regroupés par **nature du défaut**, parce que les mêmes causes reviennent.

### 2.1 Une permission d'administration exigée pour une simple lecture

Deux écrans se vidaient en silence pour tous les utilisateurs sauf le super-admin.

- **Sélecteur de signataires** (`d69b21d`, `392322d`) — `GET /users` exige
  `edit-users`, qu'aucun médecin n'a. Un 403 silencieux vidait le menu. Une route
  dédiée `/users/signataires` rend l'identifiant, le nom et l'état, sous
  `edit-reports`.
- **Modèles de compte rendu** (`fac1aaa`) — `GET /report-templates` exigeait
  `view-settings`, que **seul** le super-admin détient. Vérifié rôle par rôle en
  base. Les quatre rôles qui rédigent les comptes rendus voyaient « Aucun
  template ».

> **À retenir.** Remplir une liste déroulante n'est pas administrer. Toute route
> de lecture servant un menu doit être ouverte au rôle qui s'en sert.

### 2.2 Deux notions distinctes fusionnées en une

- **Compte rendu livré** (`59433a6`, `45aaaa1`) — Laravel modélisait la livraison
  par un drapeau `is_delivered`, **orthogonal** au statut, lequel ne connaissait
  que 0 (en attente) et 1 (terminé). La réécriture a fait de `DELIVERED` une
  valeur du statut, et la migration `V62` propage le drapeau dedans : **6 956
  comptes rendus** sur 13 907 portent ce statut. Ce sont autant de dossiers que
  le verrou rendait définitivement immodifiables. Or un complément arrive par
  nature *après* la remise — la case « Complémentaire » était condamnée au moment
  précis où elle sert.
- **Bon d'examen validé** (`6d9aec1`) — la garde refusait toute modification d'un
  bon `VALIDATED`. Laravel ne bloquait que sur `$invoice->paid != 1` : la
  contrainte d'origine est comptable, non éditoriale. Elle était de surcroît
  incohérente, un bon `DELIVERED` — plus avancé — passant sans encombre.

### 2.3 La moitié d'un mécanisme conservée

- **Opérations de caisse** (`cd73386`) — Laravel faisait deux choses à chaque
  paiement : incrémenter le solde **et** créer un `CashboxAdd`. La reprise n'avait
  gardé que la première. Le solde montait donc correctement, mais l'écran de
  fermeture, qui somme les opérations, affichait zéro malgré des dizaines de
  factures encaissées.
- **Calcul de fermeture** (`21ea952`) — la correction précédente ne suffisait pas
  pour le passé. Laravel additionnait les `cashbox_adds` *rattachés à une facture*,
  et leur montant valait `invoice.total` : sa somme est donc, ni plus ni moins,
  le total des factures réglées depuis l'ouverture. Partir directement des
  factures rend le calcul juste immédiatement, sans rattrapage.

> **À retenir.** Vérifier qu'un solde monte ne prouve pas que le mécanisme est
> complet. Le test existant contrôlait la moitié conservée.

### 2.4 Une heuristique là où le legacy avait une constante

- **État de la caisse** (`c248b9d`) — Laravel lisait `statut` sur une caisse
  **fixe** (`Cashbox::find(2)`). La reprise a rendu la caisse choisissable et
  remplacé l'identifiant figé par « la caisse de vente au solde le plus élevé ».
  L'heuristique se sabote : l'ouverture *soustrait* le fond de caisse du solde,
  si bien que la caisse ouverte peut passer derrière sa jumelle. Le bouton
  retombait sur « Ouvrir » et la session devenait impossible à fermer.
- **Résumé de fermeture** (`d4a4171`, `3b0fcf2`) — il repartait toujours de « la
  dernière session ouverte ». Avec 25 sessions jamais fermées, fermer celle du
  jour faisait basculer la référence sur une session vieille de plusieurs jours :
  876 000 encaissés, plus de deux millions affichés.

### 2.5 Recherches amputées

La recherche a été reprise en quatre temps, chacun révélant le suivant.

| Commit | Ce qui manquait | Mesure |
|---|---|---|
| `9cd6525` | téléphone, code du CR, contenu | « carcinome » : 0 → 2 129 |
| `ed76d97` | le correctif visait la mauvaise requête | `findListRows` ouvre sa clause autrement |
| `436e6fd` | accents, nom complet | « hystérectomie » : 7 → 10 ; « AHOSSI Ange » : 0 → 1 |
| `d4a4171` | pluriels du catalogue | « biopsie du seins » : 0 → 6 |
| `d0e96d3` | nom des examens sur les demandes | « Biopsies gastriques » : 0 → 2 647 |

Deux enseignements. La base **mêle les deux orthographes** — « HYSTERECTOMIE » et
« HYSTÉRECTOMIE » coexistent, héritage de vingt ans de saisie libre ; `ILIKE`
replie la casse mais pas les accents. Et les **champs sont inversés** :
`firstname` porte le nom, `lastname` le prénom, ce qui impose de chercher la
concaténation dans les deux ordres.

### 2.6 Documents PDF

- **Contenu collé depuis Word** (`a505064`) — les balises Office (`o:p`, `w:sdt`,
  `st1:place`) portent un préfixe que rien ne déclare une fois le fragment
  recollé. L'analyseur XML s'arrêtait dessus et le compte rendu devenait
  impossible à imprimer. Les éléments sont désormais *déballés*, non supprimés :
  `o:p` est vide, mais `st1:place` entoure du texte réel.
- **Typographie** (`fbb0d62`, `b8b75ee`) — polices et tailles étaient déjà
  conformes ; l'écart tenait à l'interligne. Laravel n'en déclarait aucun et
  DomPDF appliquait 12,0 pt, mesurés en installant DomPDF et en rendant un
  document avec la configuration du projet legacy. Un compte rendu de production
  a ensuite confirmé l'alignement au dixième de point.
- **Polices de l'éditeur** (`3262176`, `8d02d82`, `256367c`) — OpenHTMLToPDF ne
  reconnaît **aucun nom commercial** : « Arial » seul donne du Times. Et
  `execCommand` produit encore des `<font face=… size=…>` que le moteur ignore.
  Le choix du médecin disparaissait deux fois. Des substituts libres sont
  désormais embarqués pour les cinq polices sans équivalent.

> **À retenir.** Une substitution de police ne laisse **aucune trace** : ni
> erreur, ni journal. Seule la lecture du PDF produit la révèle. C'est pourquoi
> les tests ouvrent le document et lisent la police réellement embarquée.

### 2.7 Traçabilité

- **Modifications après signature** (`aba18c5`, `49a6e47`) — un compte rendu signé
  engage le médecin qui l'a signé. Depuis qu'il redevient modifiable, toute
  retouche est journalisée avec ses champs et son auteur, signalée aux adresses
  de `admin_mails`, et mise en exergue sur l'écran. Une empreinte prise avant
  écriture évite d'alerter sur un simple réenregistrement — une alerte banalisée
  cesse d'être lue.

---

## 3. Ce qui reste ouvert

### Bloquant pour la mise en service

**Le déploiement.** Les correctifs des 11 et 12 août ne sont pas en production.
Vérifier avec `git log -1` dans `/var/www/backend_api_labocap` avant de conclure
à un défaut de code.

**Le SMTP.** `e2ce0da` rend le code à usage unique obligatoire. En local, l'envoi
échoue et la connexion est impossible sans le drapeau `APP_OTP_LOG_PLAINTEXT`.
Si le serveur de messagerie de production ne répond pas le jour du déploiement,
**personne ne se connectera**.

**Les deux branches sont solidaires.** Quatre fonctions ont une moitié de chaque
côté : signataires, bandeau après signature, compte rendu livré, résumé de
caisse. Déployer l'une sans l'autre les laisse à moitié en place.

### Résidus de données

Les chiffres ci-dessous viennent de la **base de production**, relevés le
11 août ; la copie locale de développement ne les reproduit pas.

| Sujet | État |
|---|---|
| 25 sessions de caisse jamais fermées, dont une du 07/08 | Ne faussent plus le calcul ; l'historique reste faux |
| Deux caisses de vente **sans nom** | Indiscernables dans le sélecteur — cause de l'ouverture sur la mauvaise |
| 8 815 opérations migrées sans `invoice_id` ni `payment_method` | Journées antérieures non ventilables par mode |
| `admin_mails` | Si vide, les alertes après signature ne partent pas — silencieusement |

### Décisions en attente

- Restreindre la somme de fermeture à la caisse de vente, comme Laravel
  (`cashbox_id = 2`) : sinon un virement bancaire enregistré en opération gonfle
  le total.
- Afficher le total en cours dans la colonne « Solde de fermeture » pour les
  sessions ouvertes, plutôt qu'un 0 qui se lit comme une anomalie.
- Le complément micro ne s'affiche que si le complément macro est rempli
  (`th:if` sur `contentSupplementaire`) — discutable.
- La fiche de paie n'a **aucun équivalent legacy** ; elle reste en Arial.
- Les marges de page du compte rendu : le legacy applique `@page { margin: 1.2cm }`
  compensé d'un `-15px`. Un document de production a montré les positions
  alignées, mais aucune comparaison exhaustive n'a été faite.

### Hygiène

- `password.txt` à la racine du frontend : non suivi, **non ignoré**.
- Le mot de passe de `demo@wallyskak.com` a été divulgué en clair pendant les
  travaux — à changer.
- Les branches portent des noms qui ne décrivent plus leur contenu
  (`fix/pagination-contrats-depenses` compte 24 commits).

---

## 4. Méthode

Une seule règle a produit tous les résultats de ce document : **mesurer sur
l'artefact réel** plutôt que sur l'intention.

Le schéma de production plutôt que les entités. Le PDF produit plutôt que la
feuille de style. Les agrégats métier plutôt que les compteurs d'insertion. Le
balisage servi plutôt que le composant. La requête réellement exécutée plutôt
que celle qu'on croit avoir modifiée.

Trois erreurs de ce travail viennent d'un écart à cette règle, et sont documentées
ici pour ce qu'elles enseignent :

- un correctif de recherche appliqué à deux requêtes qui ne servaient pas l'écran
  concerné (`9cd6525` → `ed76d97`) ;
- un interligne déduit d'un paramètre de configuration au lieu d'être mesuré
  (`fbb0d62` → `b8b75ee`) ;
- un rattrapage d'opérations de caisse écrit avant d'avoir compris que la somme
  pouvait partir directement des factures (`2ef7d62`, supprimé par `21ea952`).
