# Spécification – Corrections de sécurité avant livraison du rapport L1

Cible : plateforme labsys (API `labsys_api`, front `labsys_front`, exploitation).
Référence : rapport L1, section 8.1. Chaque lot renvoie à un identifiant N-xx du registre des vulnérabilités (rapport, section 6.2).

Règles communes à tous les lots :
- flux de branches (depuis le 03/10/2026) : `develop` est la branche intermédiaire ; une branche et une PR **vers develop** par lot, nommées `fix/<lot>` ou `feat/<lot>` ; `main` ne reçoit que `develop` (ou un `hotfix/`) par PR, et c'est ce merge qui déploie en production (`deploy.yml`, tests obligatoires avant). Le workflow `protect-branches.yml` vérifie ces règles ;
- aucun lot n'est terminé sans son test automatisé qui échoue avant et passe après ;
- aucune valeur secrète dans le code ni dans les PR ; tout passe par `.env` ;
- les chemins ci-dessous sont relatifs à la racine de chaque dépôt.

Ordre d'exécution : lots 1 → 11. Les lots 1, 2, 3 sont indépendants et peuvent être menés en parallèle. Le lot 4 (montée de version) doit être terminé avant les lots 6 à 8, qui ajoutent du code.

---

## Lot 1 – Réinitialisation du mot de passe (N-01, critique)

**État au 03/10/2026** : PR #125 fusionnée et déployée en production le 02/10 (par l'ancien workflow, sans tests). Reste la configuration du serveur et la vérification sur compte réel.

**À faire**
1. ~~Relire et fusionner la PR #125.~~ Fait le 02/10.
2. Ajouter dans le `.env` de production : `APP_FRONT_URL=https://<adresse du front>` sans `/` final.
3. Déployer, puis tester « Mot de passe oublié » sur un compte réel : l'e-mail arrive, le lien ouvre `/reset-password`, le nouveau mot de passe fonctionne, le lien ne fonctionne plus une seconde fois.

**Acceptation**
- `POST /auth/forgot-password` ne renvoie jamais de jeton dans la réponse (vérifier avec `curl`).
- La colonne `users.reset_token` ne contient que des empreintes SHA-256 (64 caractères hexadécimaux).

---

## Lot 2 – Limitation des essais et verrouillage (N-02, N-10, élevée)

**Problème** : `RateLimitFilter.java` ne couvre que `/auth/login` et `/public/invoices`. Le code à usage unique (`/2fa/challenge`), `/forgot-password` et `/resend-2fa` sont sans limite : un code à 6 chiffres se trouve par force brute. L'adresse IP est lue dans `X-Forwarded-For`, que n'importe quel client peut écrire.

**À faire**
1. `RateLimitFilter.java` : étendre la liste des routes limitées à `/auth/2fa/challenge`, `/auth/forgot-password`, `/auth/resend-2fa`, `/auth/reset-password`, `/mobile/**/login` (vérifier le chemin exact des routes mobiles). Limites : 5 requêtes par minute et par adresse, 20 par heure.
2. Compteur d'échecs par **compte** sur le code à usage unique : après 5 codes faux, invalider le code et obliger à en redemander un ; après 10 échecs en 1 h, verrouiller le compte 15 minutes (`users.locked_until`, migration Flyway). La connexion sur un compte verrouillé renvoie la même erreur générique que des identifiants faux.
3. Adresse IP : ne lire `X-Forwarded-For` que si la requête vient de nginx. Deux options, au choix : `server.forward-headers-strategy: native` dans `application.yml` avec nginx qui écrase l'en-tête (`proxy_set_header X-Forwarded-For $remote_addr`), ou lire `X-Real-IP` posé par nginx. Documenter le choix dans `docker-compose.yml` et dans la configuration nginx (à versionner, voir lot 9).
4. Journaliser chaque dépassement de limite et chaque verrouillage (niveau WARN, e-mail masqué, IP).

**Acceptation**
- Test d'intégration : 6 tentatives de code faux en moins d'une minute → la 6ᵉ renvoie 429 ; 10 échecs → le compte est verrouillé, la connexion échoue même avec le bon mot de passe pendant 15 minutes.
- Test unitaire : une requête avec `X-Forwarded-For: 1.2.3.4` venant d'une adresse non nginx est comptée sur l'adresse réelle.
- Les tests obsolètes `AuthIntegrationTest.login_success` et `FileControllerIT` (fichiers publics) sont corrigés ou supprimés dans ce lot.

---

## Lot 3 – Front : XSS, CSP et en-têtes (N-07, élevée)

**Problème** : `src/app/(dashboard)/reports/suivi/page.tsx:472-525` injecte une signature SVG venant de l'API avec `dangerouslySetInnerHTML` sans nettoyage. `RichTextEditor.tsx:186-190` réinjecte le HTML du compte rendu. La CSP est en mode « observation » (`CSP_ENFORCE=false` par défaut dans `docker-compose.yml:23`). `next.config.ts` ne pose aucun en-tête HSTS, nosniff ni Referrer-Policy. Le cache React Query n'est pas vidé à la déconnexion.

**À faire**
1. Ajouter `dompurify` (et `@types/dompurify`). Nettoyer la signature SVG avec le profil SVG de DOMPurify (`USE_PROFILES: { svg: true }`, interdire `<script>`, `on*`, `<foreignObject>`, `href` javascript:). Nettoyer `value` dans `RichTextEditor` avant `innerHTML`.
2. Côté API, nettoyer aussi la signature à l'enregistrement (jsoup, liste blanche SVG) : la défense ne repose pas sur le seul front.
3. `CSP_ENFORCE=true` par défaut dans `docker-compose.yml`. Avant de l'activer en production : 48 h en observation avec lecture des rapports `/api/csp-report`, corriger les violations, puis activer.
4. `next.config.ts` → `headers()` sur toutes les routes : `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: camera=(), microphone=(), geolocation=()` (adapter si l'appli web utilise le micro pour les appels).
5. Déconnexion (`topbar.tsx:50-60`) : appeler `queryClient.clear()` puis `window.location.assign('/login')` au lieu de `router.push`, pour vider la mémoire. Retirer `signature` et `phone` du profil persisté en `localStorage` (`auth.store.ts:82-88`), ne garder que ce qui sert au rendu avant revalidation.
6. CI (`.github/workflows/deploy.yml:25`) : Node 22. Ajouter `npm audit --audit-level=high` avant le build. Retirer les dépendances inutilisées : FullCalendar (5 paquets), exceljs, next-themes, date-fns.

**Acceptation**
- Test (vitest, à mettre en place dans ce lot) : une signature `<svg onload="alert(1)">` rendue dans la page de suivi ne contient plus `onload`.
- `curl -I https://<front>/login` montre les 4 en-têtes et une `Content-Security-Policy` (pas `-Report-Only`).
- Observatoire Mozilla (observatory.mozilla.org) : note A ou mieux sur le front et sur l'API.

---

## Lot 4 – Mise à niveau du socle et analyse des dépendances (N-06, élevée)

**Problème** : Spring Boot 3.3.0 (`pom.xml:10`) est hors support depuis le 30/06/2026. Aucune analyse des dépendances connues vulnérables n'est faite, ni côté API ni côté front.

**À faire**
1. Monter vers la dernière branche Spring Boot supportée à la date de l'intervention (vérifier sur spring.io/projects/spring-boot#support ; au 02/10/2026, c'est une branche 4.x). Prévoir : Spring Framework 7, Spring Security 7, Jakarta EE 11, Hibernate 7. Faire la montée par étapes (3.3 → 3.5 → 4.x), en exécutant les 992 tests à chaque étape.
2. Remplacer `openhtmltopdf` (sans version depuis 2021) par `flying-saucer` maintenu ou `openpdf` + `jsoup`, à condition de valider le rendu des quatre gabarits PDF (comptes rendus, factures, tickets, étiquettes) sur un jeu de 10 documents réels comparés visuellement.
3. Mettre à jour `googleauth` (2FA) ou le remplacer par une implémentation TOTP maintenue (`dev.samstevens.totp`).
4. Analyse des dépendances : plugin `org.owasp:dependency-check-maven` dans le `pom.xml`, échec du build au-delà de CVSS 7 ; `npm audit --audit-level=high` côté front ; activer Dependabot (ou Renovate) sur les deux dépôts avec une PR hebdomadaire.
5. Épingler l'image de base `eclipse-temurin:21-jre-alpine` sur un tag de version précis, mis à jour par Dependabot.

**Acceptation**
- `mvn -B verify` passe avec 0 test ignoré sur la nouvelle version.
- `dependency-check` ne remonte aucune vulnérabilité ≥ 7 non justifiée (fichier de suppression documenté s'il y en a).
- Les quatre gabarits PDF sont validés par le laboratoire.

---

## Lot 5 – Sauvegardes et reprise (N-03, élevée)

**Problème** : `ScheduledTasksService.java:204-257` fait un `pg_dump` quotidien à 18 h 30 dans un volume Docker du **même serveur**, en clair, sans copie externe, sans alerte (simple `log.error`), sans sauvegarde des fichiers, sans procédure de restauration. Fuseau horaire du planificateur non fixé.

**À faire**
1. Chiffrer chaque export avant stockage : `age` (clé publique dans le conteneur, clé privée conservée hors serveur, chez le prestataire ET chez le laboratoire, sous enveloppe scellée) ou `gpg`. Jamais de clé privée sur le serveur.
2. Copier chaque export chiffré vers un bucket S3 **distinct** du bucket de fichiers, avec versionnage activé, règle de cycle de vie (30 versions quotidiennes, 12 mensuelles), accès en écriture seule pour le serveur (politique IAM sans `s3:DeleteObject`). Région : [à décider avec le laboratoire, voir rapport §8.2 sur le pays d'hébergement].
3. Sauvegarder aussi le volume `storage` (fichiers locaux de secours) par synchronisation quotidienne vers le même bucket.
4. Alerte : si l'export échoue, est vide, ou si la copie S3 échoue → e-mail aux adresses `admin_mails` + e-mail au prestataire. Vérification quotidienne indépendante : un contrôle à 20 h vérifie qu'un objet daté du jour existe sur S3, sinon alerte.
5. Fixer le fuseau : `spring.jackson.time-zone` et `@Scheduled(zone = "Africa/Porto-Novo")` sur toutes les tâches, ou `TZ=Africa/Porto-Novo` dans le conteneur.
6. Rédiger `docs/restauration.md` : prérequis, récupération de l'export sur S3, déchiffrement, restauration sur un serveur vierge avec `docker compose`, restauration des fichiers, vérifications post-restauration (comptages patients / demandes / comptes rendus / factures, ouverture d'un PDF, connexion). Objectif : exécutable par un ingénieur qui ne connaît pas le projet, en moins de 4 h.
7. Réaliser le test de restauration en présence de l'interlocuteur du laboratoire, chronométré, sur un serveur ou une machine vierge. Remplir le procès-verbal (modèle : rapport L1, annexe C). Signer.

**Acceptation**
- Un objet chiffré daté du jour existe sur S3 chaque jour pendant 7 jours consécutifs.
- Simuler un échec (`pg_dump` renommé) → l'alerte arrive dans les 10 minutes.
- Le procès-verbal de restauration est signé, avec RTO mesuré ≤ 4 h et comptages identiques.

---

## Lot 6 – Journal des consultations et conservation des journaux (N-04, N-09, N-12, élevée)

**Problème** : rien ne trace qui a **lu** un dossier patient, une demande, un compte rendu ou un fichier (seule l'impression PDF l'est). Le journal applicatif est conservé 30 jours (`application.yml:236`) dans le conteneur, donc perdu au redémarrage. Dans `log_reports`, l'auteur des actions CREATE/UPDATE est cherché avec l'identifiant de branche au lieu de l'identifiant utilisateur (`ReportServiceImpl.java:333, 468`), le champ reste vide. La suppression d'un compte rendu n'est pas journalisée (`ReportServiceImpl.java:720-723`).

**À faire**
1. Migration Flyway : table `journal_acces` (`id`, `at`, `user_id`, `branch_id`, `action` ∈ {READ, DOWNLOAD, PRINT, EXPORT}, `entity_type` ∈ {PATIENT, TEST_ORDER, REPORT, FILE, INVOICE}, `entity_id`, `ip`, `user_agent_hash`). Index sur (`entity_type`, `entity_id`) et sur (`user_id`, `at`).
2. Écriture : un aspect ou un intercepteur sur les contrôleurs de lecture `GET /patients/{id}`, `GET /test-orders/{id}`, `GET /reports/{id}`, `GET /files/**`, `GET /invoices/{id}`, et sur les routes mobiles équivalentes. Écriture asynchrone (`@Async`) pour ne pas ralentir la réponse ; la perte d'une écriture est journalisée en WARN.
3. Lecture : `GET /audit/acces?entityType=&entityId=&userId=&from=&to=` protégé par une permission `VIEW_AUDIT`, paginé. Écran correspondant dans le front sous Administration → Journal d'accès (lecture seule, filtres, export CSV).
4. Rétention : tâche mensuelle qui purge `journal_acces` et `log_reports` au-delà de **12 mois** (constante documentée). Interdire toute route de modification ou suppression sur ces tables.
5. Journal applicatif : `LOG_FILE=/var/log/labsys/api.log` passé au conteneur, volume persistant, rotation à 365 jours (`logging.logback.rollingpolicy.max-history: 365`). Vérifier que le dossier est accessible en écriture par l'utilisateur non-root du conteneur.
6. Corriger la recherche d'auteur dans `ReportServiceImpl` (lignes 333 et 468) : utiliser l'identifiant de l'utilisateur courant. Journaliser la suppression d'un compte rendu (action DELETE dans `log_reports`).

**Acceptation**
- Test d'intégration : ouvrir un compte rendu → une ligne `READ / REPORT` avec le bon `user_id` apparaît ; télécharger son PDF → `DOWNLOAD / FILE`.
- Test : créer puis modifier un compte rendu → `log_reports.user` renseigné sur les deux lignes ; supprimer → ligne DELETE.
- Test de la purge : une ligne datée de 13 mois disparaît, une ligne de 11 mois reste.
- Après `docker compose restart api`, le journal applicatif de la veille est toujours présent.

---

## Lot 7 – Contrôle d'accès par ressource (N-05, N-11, élevée)

**Problème** : `FileController.java:29-41` sert tout fichier à tout utilisateur connecté qui connaît son chemin, et certains noms sont prévisibles (`documents/<timestamp>.pdf`). Les services font 316 `findById` contre 76 `findByIdAndBranchId`. `DashboardController.java:21-105` expose 13 routes (chiffre d'affaires, utilisateurs connectés) sans `@PreAuthorize`.

**À faire**
1. Fichiers : ne plus servir un fichier par son chemin brut. Chaque fichier est rattaché à une entité (`stored_files` : `id`, `path`, `entity_type`, `entity_id`, `branch_id`). La route devient `GET /files/{id}` ; avant de servir, le service charge l'entité propriétaire et applique la même règle de permission que pour la lire (permission métier + branche). Les anciens chemins sont migrés dans la table par un script Flyway.
2. Services : passer systématiquement par `findByIdAndBranchId` (ou un `BranchScopedRepository` de base) pour toutes les entités rattachées à une branche. Lister les 316 occurrences, traiter celles qui touchent patients, demandes, comptes rendus, factures, fichiers, employés ; justifier par un commentaire celles qui restent globales (paramètres, catalogue).
3. Tableau de bord : `@PreAuthorize("hasAuthority('VIEW_DASHBOARD')")` sur le contrôleur, et `VIEW_DASHBOARD_FINANCE` sur les routes de chiffre d'affaires et de caisse. Ajouter ces permissions à la migration des rôles et au front (`PERMISSIONS.*`, menu).
4. Médecins prescripteurs (web et mobile) : un médecin ne voit que les demandes et comptes rendus dont il est prescripteur ou signataire. Vérifier la règle sur les routes `GET /test-orders`, `GET /reports`, recherche, et sur l'index mobile.

**Acceptation**
- Test d'intégration : un utilisateur de la branche A demande un fichier de la branche B → 403 ; sans la permission de lire les comptes rendus, demande le PDF d'un compte rendu → 403.
- Test : un médecin demande la liste des demandes → seules les siennes.
- Test : utilisateur sans `VIEW_DASHBOARD` → 403 sur les 13 routes.

---

## Lot 8 – Intégrité des comptes rendus validés (N-08, moyenne)

**Problème** : un compte rendu validé reste modifiable (choix assumé, modification tracée et signalée) mais l'ancien texte n'est pas conservé : la trace dit *quels champs* ont changé, pas *ce qu'ils contenaient*. `POST /reports` avec un `reportId` (`ReportController.java:190-195`) modifie un compte rendu sans passer par la trace « après signature ».

**À faire**
1. Migration Flyway : table `report_versions` (`id`, `report_id`, `version`, `content`, `title`, `conclusion`, `signataires`, `status`, `saved_at`, `saved_by`). Avant chaque mise à jour d'un compte rendu dont le statut est validé ou livré, enregistrer l'état complet courant. Lecture : `GET /reports/{id}/versions` et `GET /reports/{id}/versions/{n}` avec la permission `VIEW_REPORT_HISTORY`. Front : onglet « Versions » sur le compte rendu, comparaison côte à côte de deux versions (texte brut suffit).
2. `POST /reports` : refuser (`400`) tout corps contenant un `reportId`. La modification passe uniquement par `PUT /reports/{id}`, qui porte la trace.
3. Règle métier à confirmer avec le laboratoire : qui peut modifier un compte rendu livré ? Proposition : seul un pathologiste signataire, avec saisie obligatoire d'un motif (`reason`, 20 caractères minimum), enregistré dans `log_reports` et dans l'e-mail d'alerte. Si le laboratoire préfère l'interdiction, remplacer par un refus `409` et un flux « addendum ».
4. Les tables `log_reports`, `report_versions`, `journal_acces` sont en ajout seul : aucun `UPDATE`/`DELETE` applicatif ; révoquer ces droits à l'utilisateur PostgreSQL de l'application sur ces tables (migration avec `REVOKE`).

**Acceptation**
- Test : modifier un compte rendu validé → une ligne dans `report_versions` avec l'ancien contenu ; la version précédente est relisible.
- Test : `POST /reports` avec `reportId` → 400.
- Test : `UPDATE report_versions` avec le rôle applicatif → erreur de permission PostgreSQL.

---

## Lot 9 – Chaîne de déploiement et tests (N-13, moyenne)

**État au 03/10/2026** : points 1 et 5 livrés dans develop (PR #126 `fix/suite-de-tests-et-ci`, PR #128 `fix/protection-flux-develop`), en attente de la PR #127 develop → main. La suite a été remise au vert au passage (1320 tests, 4 ignorés : `FileDuMedecinSurBaseTest`, sur base désignée). Restent les points 2, 3 (seule `APP_FRONT_URL` est transmise), 4, et les trois contrôles d'acceptation.

**Problème** : `.github/workflows/deploy.yml` déploie l'API sans compiler ni tester (`Dockerfile:12` saute les tests). Le front n'a aucun test. Des variables ne sont pas transmises au conteneur (`FLUIDPAY_SMS_*`, `PUBLIC_BASE_URL`, `SECRETS_KEY`, `LOG_FILE`). La configuration nginx et TLS n'est pas versionnée.

**À faire**
1. ~~API : étape `mvn -B verify` (tests inclus, avec Testcontainers) **avant** le déploiement ; échec → pas de déploiement. Garder le `-DskipTests` dans l'image (les tests ont déjà tourné en CI).~~ Fait (`ci.yml`, appelé par `deploy.yml`).
2. Front : `vitest` + `@testing-library/react`. Tests minimaux : `proxy.ts` (redirection sans cookie, passage avec cookie, verrouillage de `/login` pendant un challenge 2FA), `buildCspPolicy` (présence de `strict-dynamic`, nonce, pas d'`unsafe-inline` sur `script-src`), intercepteur 401 → refresh → retry (`client.ts`), fusion des permissions dans `setUser`. Étape `npm test` dans la CI.
3. `docker-compose.yml` : transmettre `FLUIDPAY_SMS_*`, `PUBLIC_BASE_URL`, `SECRETS_KEY`, `LOG_FILE`, `APP_FRONT_URL`. Ajouter un contrôle au démarrage qui refuse de lancer l'API en profil `prod` si une variable obligatoire manque (déjà le cas pour certaines avec `:?`, généraliser).
4. Versionner la configuration nginx (`infra/nginx/`) : TLS 1.2 minimum, suites modernes, HSTS, `proxy_set_header X-Forwarded-For $remote_addr`, taille max de corps, timeouts. Renouvellement automatique du certificat (certbot) documenté.
5. ~~Déploiement : `docker compose up -d --build` avec `--wait` et contrôle de `/actuator/health` ; si le nouveau conteneur ne devient pas sain, revenir à l'image précédente (tag `previous`) automatiquement.~~ Fait. Délai effectif ≈ 3 min 30 (healthcheck du compose : `start_period` 60 s + 5 essais × 30 s), au lieu des 2 min prévues : acceptable, le conteneur précédent sert pendant ce temps.

**Acceptation**
- Un commit qui casse un test n'est pas déployé (vérifier en poussant volontairement un test rouge sur une branche de test).
- `docker compose config` montre toutes les variables attendues.
- SSL Labs : note A sur `api.caap.bj` et sur le front.

---

## Lot 10 – Supervision et détection (N-09, moyenne)

**Problème** : aucune mesure de disponibilité (le TdR attend 99 %), aucune alerte sur les tentatives répétées, aucune alerte en cas d'indisponibilité.

**À faire**
1. Sonde externe (Uptime Kuma auto-hébergé sur une autre machine, ou healthchecks.io / Better Uptime) toutes les minutes sur `GET /actuator/health` de l'API et sur la page de connexion du front. Alerte e-mail + SMS (via FluidPay) au prestataire après 2 échecs consécutifs ; rapport mensuel de disponibilité en heures ouvrées (lundi–vendredi 7 h 30–18 h 30, samedi 8 h–13 h) envoyé à la direction.
2. Alerte sur tentatives : si plus de 20 échecs de connexion ou de code en 10 minutes (toutes adresses confondues) ou plus de 5 verrouillages de comptes en 1 h → e-mail `admin_mails` + prestataire. Implémentation simple : compteur en mémoire dans `RateLimitFilter` / `AuthServiceImpl`, tâche toutes les 10 minutes.
3. Alerte sur erreurs serveur : plus de 10 réponses 5xx en 5 minutes → e-mail au prestataire.
4. Métriques : activer `actuator/metrics` et `prometheus` sur le réseau interne uniquement (pas exposé par nginx) ; conserver 90 jours (Prometheus + Grafana sur le serveur, ou export vers un service géré). Tableau : disponibilité, temps de réponse p95 par route, erreurs, espace disque, sauvegardes réussies.
5. Alerte disque : < 15 % libre → e-mail.

**Acceptation**
- Arrêter le conteneur API 3 minutes → alerte reçue, puis alerte de retour.
- 25 connexions ratées en 10 minutes depuis un script → alerte reçue.
- Le rapport mensuel de disponibilité est généré pour le mois en cours.

---

## Lot 11 – Durcissement complémentaire (N-14, N-16, faible à moyenne)

**À faire**
1. Chiffrer `users.two_factor_secret` en base avec un `AttributeConverter` AES-256-GCM utilisant la clé `SECRETS_KEY` (le composant `ChiffreurDeSecrets` existe déjà) ; migration de données en une passe au démarrage, idempotente.
2. Retirer du dépôt les 13 fichiers de données sous `storage/` (`git rm --cached`, `.gitignore`). Si ce sont des données réelles : purger l'historique (`git filter-repo`) et le signaler au laboratoire comme incident mineur.
3. Politique de mot de passe : 12 caractères minimum, refus des 10 000 mots de passe les plus courants (liste embarquée), refus si contient l'e-mail ou le nom. Appliquée à la création, au changement et à la réinitialisation. Le front affiche la règle et un indicateur de force.
4. Confirmer et documenter l'état de production de `S3_ENABLED` et `STORAGE_ENCRYPTION_ENABLED` ; si désactivés, activer après sauvegarde (lot 5) et migrer les fichiers existants vers S3 chiffré par le script de synchronisation.
5. Code d'accès mobile (`AccesMobile.tsx`) : limiter le code d'enrôlement à 15 minutes et à un seul usage ; chaque appareil reste révocable individuellement.
6. E-mail en clair dans le cookie `pending_2fa_email` : le remplacer par un identifiant opaque côté serveur.

**Acceptation**
- La colonne `two_factor_secret` ne contient plus de Base32 lisible.
- `git ls-files storage` est vide.
- Test : mot de passe `password2026` refusé ; `Motdepasse!` refusé (liste) ; 12 caractères aléatoires acceptés.

---

## Hors code – à faire par le prestataire avec le laboratoire

| Action | Qui | Preuve attendue |
|---|---|---|
| Changer tous les secrets de l'ancienne plateforme (MySQL, SMTP `mail@caap.bj`, FedaPay, PayDunya, KKiaPay, clé SMS, jeton OurVoice, jeton e-MECeF) | Prestataire + laboratoire | Liste des secrets changés, datée |
| Arrêter l'ancienne plateforme ; vérifier que `gestion.caap.bj` ne sert plus `/archive.zip`, `/storage/backup/`, `/storage/documents/` | Prestataire | Captures des 404, date |
| Test de restauration (lot 5) | Prestataire + interlocuteur du laboratoire | Procès-verbal signé |
| Recette L2 | Laboratoire | Procès-verbal signé |
| Mesures de performance avant/après sur 3 écrans | Prestataire | Tableau (rapport §5.6) |
| Captures d'écran avant/après | Prestataire | Rapport §5.4 |

---

## Suivi

| Lot | Branche (PR vers develop) | PR | Dans develop | En production | Vérifié par |
|---|---|---|---|---|---|
| 1 | fix/reinitialisation-mot-de-passe | #125 (vers main, avant develop) | oui | 02/10/2026 | |
| 2 | fix/limitation-essais | #131 | 03/10/2026 | attend #127 | |
| 3 | fix/front-xss-csp-entetes (dépôt front) ; API : dans #130 | front #106 | 03/10/2026 | attend #127 | |
| 4 | chore/spring-boot-4 | #137 | 03/10/2026 | attend #127 | |
| 5 | feat/sauvegardes-externes | #133 | 03/10/2026 | attend #127 | |
| 6 | feat/journal-acces (+ front feat/journal-acces-front) | #132, front #108 | 03/10/2026 | attend #127 | |
| 7 | fix/acces-par-ressource (+ front fix/acces-par-ressource-front) | #136, front #111 | 03/10/2026 | attend #127 | |
| 8 | feat/versions-comptes-rendus | #134 | 03/10/2026 | attend #127 | |
| 9 (points 1, 5) | fix/suite-de-tests-et-ci, fix/protection-flux-develop | #126, #128 | 03/10/2026 | attend #127 | |
| 9 (points 2, 3, 4) | chore/ci-tests-nginx ; point 2 dans front #106 et #109 | #129 | 03/10/2026 | attend #127 | |
| 10 | feat/supervision | #135 | 03/10/2026 | attend #127 | |
| 11 | fix/durcissement (+ front fix/durcissement-front) | #130, front #107 | 03/10/2026 | attend #127 | |

Mises en production : une PR develop → main par lot livré (ou par groupe de lots), fusionnée après revue ; #127 est la première.
