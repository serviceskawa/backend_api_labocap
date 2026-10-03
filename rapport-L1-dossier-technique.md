> **CONTRÔLE AVANT REMISE – à retirer dans la version livrée**
>
> Ce rapport décrit la plateforme **après** exécution de `SPEC-corrections-securite.md`. Il ne doit être remis à la direction qu'une fois les points suivants vrais :
>
> - [ ] Lots 1 à 11 de la spécification fusionnés et déployés en production (tableau de suivi de la spec rempli)
> - [ ] Test de restauration réalisé avec le laboratoire, procès-verbal signé
> - [ ] Anciens secrets changés, ancienne plateforme arrêtée (preuves datées)
>
> Un rapport remis avant cela affirmerait à la direction, et le cas échéant à l'APDP, des mesures qui n'existent pas encore sur des données de santé.

---

# Rapport de prestation (L1)
## Renforcement de la sécurité de la plateforme de gestion du laboratoire CAAP

---

## 0. Contrôle documentaire

| | |
|---|---|
| Document | Rapport de prestation – livrable L1 du TdR « Renforcement de la sécurité de la plateforme de gestion du laboratoire et de ses données » |
| Destinataire | Direction du CAAP |
| Classification | **Confidentiel** – contient la description de vulnérabilités et de l'architecture de production |
| Prestataire | KAWA SERVICES |
| Interlocuteur du laboratoire | ELGA DOSSOU-YOVO |

---

## Glossaire

Les termes sont classés par ordre alphabétique.

| Terme | Définition |
|---|---|
| Adresse IP | Numéro qui identifie un ordinateur ou un téléphone sur internet, comme une adresse postale |
| AES-256-GCM | Procédé de chiffrement de référence, utilisé ici pour rendre les fichiers illisibles sans la clé |
| Affectation de masse | Défaut d'un logiciel qui accepte, dans un formulaire, des champs qu'il n'aurait pas dû accepter |
| Alerte | Message automatique (e-mail ou SMS) envoyé dès qu'un problème est détecté |
| APDP | Autorité de Protection des Données Personnelles du Bénin |
| API | Partie « serveur » de la plateforme : elle applique les règles métier et de sécurité ; l'interface web et l'application mobile lui envoient leurs demandes |
| Application mobile | Application installée sur un téléphone, utilisée ici par les médecins et le personnel |
| ASVS | Référentiel de l'OWASP qui liste les exigences de sécurité qu'une application doit satisfaire, par niveau |
| Authentification | Vérification de l'identité d'un utilisateur à la connexion |
| BCrypt | Procédé qui transforme un mot de passe en empreinte illisible avant de le stocker ; le mot de passe lui-même n'est jamais conservé |
| Bucket | Espace de stockage de fichiers chez Amazon Web Services (voir S3) |
| CDN | Serveur tiers depuis lequel un site charge des scripts ou des polices ; une dépendance externe que l'on ne contrôle pas |
| Chaîne de déploiement | Suite d'étapes automatiques qui vérifient le code (compilation, tests, analyse) puis le mettent en service |
| Chiffrement | Transformation d'une donnée pour qu'elle ne soit lisible qu'avec une clé |
| Cloisonnement par site | Règle qui empêche un utilisateur d'un site de voir les données d'un autre site |
| Code à usage unique | Code à six chiffres reçu par e-mail ou généré par une application, demandé à chaque connexion en plus du mot de passe (voir Double authentification) |
| Compilation | Transformation du code écrit par les développeurs en programme exécutable |
| Composant | Brique logicielle fournie par un tiers (framework, bibliothèque, base de données) sur laquelle la plateforme repose |
| Conteneur (Docker) | Enveloppe isolée dans laquelle tourne chaque partie de la plateforme ; une faille dans l'une ne donne pas accès aux autres |
| Cookie | Petit fichier déposé par le site dans le navigateur, qui sert ici à reconnaître la session de l'utilisateur. **HttpOnly** : le cookie ne peut pas être lu par un script. **SameSite** : le cookie n'est envoyé qu'au site qui l'a créé |
| CORS | Liste des sites autorisés à interroger l'API depuis un navigateur |
| coturn / relais d'appels | Logiciel qui permet aux appels audio par internet de passer même derrière un réseau d'entreprise ou un opérateur mobile |
| CSP | Règle transmise au navigateur qui lui interdit d'exécuter tout script non prévu par la plateforme |
| CSRF | Attaque qui fait exécuter une action à un utilisateur connecté à son insu, par exemple en cliquant sur un lien |
| CVE | Numéro d'identification public d'une vulnérabilité connue dans un logiciel |
| CVSS | Échelle de 0 à 10 qui note la gravité d'une vulnérabilité : Critique ≥ 9, Élevée 7 à 8,9, Moyenne 4 à 6,9, Faible < 4 |
| Dependabot, Dependency-Check, npm audit | Outils qui vérifient automatiquement si un composant utilisé a une vulnérabilité connue |
| Dépendance | Synonyme de composant |
| Dépôt (de code) | Espace où le code source est conservé avec tout son historique de modifications |
| DGI, e-MCF, e-MECeF | Direction Générale des Impôts et son système de facturation normalisée, auquel les factures doivent être transmises |
| DOMPurify | Outil qui nettoie un contenu avant affichage pour en retirer tout script caché |
| Double authentification (2FA) | Connexion en deux étapes : mot de passe, puis code à usage unique |
| En-têtes de sécurité | Instructions envoyées par le serveur au navigateur pour le protéger : forcer le HTTPS (HSTS), interdire l'affichage dans un cadre, etc. |
| Fin de support | Date à partir de laquelle l'éditeur d'un composant ne corrige plus ses failles de sécurité |
| Firebase | Service de Google utilisé pour envoyer les notifications sur les téléphones |
| FluidInvoice | Service de facturation normalisée, relié à la DGI |
| FluidPay | Service d'envoi de SMS et d'appels vocaux |
| Framework | Socle logiciel sur lequel une application est construite (Laravel pour l'ancienne plateforme, Spring Boot et Next.js pour la nouvelle) |
| GTI / GTR | Garantie de temps d'intervention (délai pour prendre en charge un incident) et garantie de temps de rétablissement (délai pour le résoudre) |
| Hachage | Voir BCrypt |
| HTTPS / TLS | Chiffrement des échanges entre le navigateur et le serveur ; le cadenas dans le navigateur |
| IDOR | Accès à la donnée d'un autre patient ou d'une autre facture en changeant simplement un numéro dans l'adresse de la page |
| Injection de script (XSS) | Introduction d'un code malveillant dans une page, exécuté chez les autres utilisateurs qui l'ouvrent |
| Injection SQL | Attaque qui détourne une requête vers la base de données pour en lire ou modifier le contenu |
| Interface web | Partie de la plateforme utilisée dans le navigateur |
| ISO 27001 / 27002 | Normes internationales de gestion de la sécurité de l'information et catalogue des mesures associées |
| ISO 22301 | Norme internationale de continuité d'activité (sauvegardes, reprise après incident) |
| ISO 27701 | Extension de l'ISO 27001 pour la protection des données personnelles |
| ISO 15189 | Norme internationale des laboratoires de biologie médicale ; son chapitre 7.11 traite des systèmes d'information |
| Java, PHP | Langages de programmation (nouvelle et ancienne plateforme) |
| Jeton (JWT) | Preuve de connexion délivrée après authentification, à durée de vie courte, renouvelée tant que l'utilisateur est actif |
| Journal (d'accès, d'audit) | Enregistrement de qui a fait quoi, et quand |
| Laravel | Framework de l'ancienne plateforme, sans correctifs de sécurité depuis janvier 2023 |
| Limitation des essais | Blocage temporaire après un nombre d'échecs de connexion, pour empêcher de deviner un mot de passe ou un code |
| LTS | Version d'un composant bénéficiant d'un support prolongé |
| MariaDB / MySQL | Base de données de l'ancienne plateforme |
| Migration (de données) | Transfert des données de l'ancienne base vers la nouvelle |
| Mode débogage | Réglage de développement qui affiche des informations techniques en cas d'erreur ; dangereux en production |
| Next.js, React | Technologies de la nouvelle interface web |
| nginx | Serveur web placé devant la plateforme ; il reçoit les connexions, applique le HTTPS et transmet à l'API |
| NIST CSF | Cadre américain de gestion de la cybersécurité, utilisé comme grille de lecture pour la direction |
| OWASP Top 10 | Liste des dix catégories de failles les plus courantes dans les applications web |
| Permission | Droit précis accordé à un rôle, par exemple « valider un compte rendu » |
| PostgreSQL | Base de données de la nouvelle plateforme |
| Procès-verbal de restauration | Document signé attestant qu'une sauvegarde a été remise en service avec succès, en présence du laboratoire |
| Référentiel | Norme ou guide de bonnes pratiques reconnu internationalement |
| Révocation | Annulation immédiate d'une session ou d'un appareil, par exemple à la perte d'un téléphone |
| Rôle | Ensemble de permissions attribué à un utilisateur selon sa fonction (secrétaire, technicien, pathologiste, comptable…) |
| RPO | Quantité maximale de données perdues en cas d'incident, exprimée en durée (TdR : 24 h) |
| RTO | Délai maximal de remise en service après un incident (TdR : 4 h) |
| S3 | Service de stockage de fichiers d'Amazon Web Services, utilisé pour les fichiers médicaux et les sauvegardes |
| Sauvegarde hors site | Copie des sauvegardes sur un emplacement distinct du serveur, pour survivre à sa panne |
| Serveur dédié | Serveur réservé à la seule plateforme |
| Session | Période pendant laquelle un utilisateur connecté est reconnu ; elle se ferme après inactivité |
| Spring Boot | Framework de la nouvelle API |
| SRI | Contrôle d'intégrité d'un script chargé depuis un CDN |
| Supervision | Surveillance automatique de la disponibilité et du bon fonctionnement, avec alertes |
| Suppression logique | Marquage d'une donnée comme supprimée sans l'effacer réellement, pour conserver l'historique |
| Support (éditeur) | Période pendant laquelle l'éditeur d'un composant publie des correctifs de sécurité |
| SVG | Format d'image, utilisé pour les signatures ; il peut contenir du script, d'où la nécessité de le nettoyer |
| Test automatisé | Programme qui vérifie qu'une fonction se comporte comme prévu, exécuté à chaque modification du code |
| Test d'intégration | Test automatisé exécuté sur une vraie base de données |
| TOTP | Code à usage unique généré par une application d'authentification sur le téléphone |
| Traçabilité | Capacité à savoir qui a consulté, créé ou modifié une donnée, et quand |
| vCPU, Go, NVMe | Unités de puissance de calcul, de mémoire et de disque d'un serveur |
| Versionnage | Conservation de chaque état successif d'un fichier ou d'un compte rendu |
| Vulnérabilité | Faiblesse d'un logiciel qu'un attaquant peut exploiter |
| WebRTC | Technologie d'appel audio par internet, utilisée entre l'application mobile et le laboratoire |
| XSS | Voir Injection de script |

---

## 1. Résumé exécutif

### 1.1 Objet de la prestation et réalisations

La plateforme en service depuis 2022, développée par KAWA SERVICES, reposait sur les composants de référence de l'époque. Faute de contrat de maintenance, elle n'a pas suivi leur cycle de vie : le framework Laravel 8 ne reçoit plus de correctifs de sécurité depuis le 24 janvier 2023 et PHP 8.1 depuis le 31 décembre 2025. L'audit, conduit selon les référentiels actuels et au regard des exigences nouvelles de l'APDP, a relevé vingt-et-une vulnérabilités, dont dix de niveau « élevé ». Quatre d'entre elles exposaient directement des données de santé : des sauvegardes complètes de la base et des comptes rendus de patients accessibles sans mot de passe, des secrets de configuration conservés dans l'historique du code, et des comptes rendus validés modifiables sans trace.

Corriger cette plateforme n'était pas viable : la couche d'autorisation, la traçabilité et le stockage des fichiers étaient à reprendre, sans aucun test automatisé pour sécuriser les modifications. Le laboratoire a donc été doté d'une **nouvelle plateforme**, développée sur des composants récents et maintenus, avec une interface entièrement refaite, une base de données PostgreSQL, un stockage de fichiers chiffré, des sauvegardes chiffrées copiées hors du serveur, un journal des accès, une supervision, et de nouvelles fonctions (appels par internet, notifications vocales en langue locale, SMS, facturation normalisée intégrée). L'intégralité des données a été reprise. La plateforme sert également de socle à l'application mobile, objet d'une prestation distincte engagée en 2025 et finalisée en 2026.

La nouvelle plateforme a elle-même été auditée selon le même référentiel que l'ancienne. Les seize points relevés ont été corrigés et vérifiés avant la remise de ce rapport (section 6.2).

### 1.2 Évaluation des critères de réussite (TdR §2.3)

| Résultat attendu | Critère du TdR | Verdict |
|---|---|---|
| L'état de sécurité est connu | État des lieux remis et présenté | **Atteint** – section 3 |
| Les faiblesses graves sont corrigées | Toutes les faiblesses « critiques » ou « élevées » traitées et vérifiées | **Atteint** – les 10 vulnérabilités élevées de l'ancienne plateforme sont fermées par la nouvelle (section 6.1) ; les 7 points critiques ou élevés relevés sur la nouvelle plateforme sont corrigés et vérifiés par des tests automatisés (section 6.2) |
| La plateforme est à jour | Tous les composants bénéficient d'un support de sécurité | **Atteint** – inventaire en section 5.1 ; une analyse automatique des dépendances bloque tout déploiement comportant une vulnérabilité connue |
| Les données peuvent être restaurées | Test de restauration réussi en présence du laboratoire | **Atteint** – test réalisé en présence du laboratoire, délai de reprise mesuré inférieur aux 4 heures attendues, procès-verbal signé |

### 1.3 Conformité aux fonctions attendues F1 à F6

| N° | Fonction | Niveau attendu | Niveau atteint | Mesures |
|---|---|---|---|---|
| F1 | Confidentialité | Aucun accès non autorisé | Atteint | Code à usage unique obligatoire, 109 permissions vérifiées par l'API, contrôle par dossier et par site, fichiers chiffrés servis uniquement après contrôle de droit, limitation des essais et verrouillage des comptes |
| F2 | Intégrité | Modification d'un compte rendu validé impossible sans trace | Atteint | Toute modification après signature conserve la version précédente, est tracée avec son motif et signalée aux administrateurs ; les journaux sont en ajout seul au niveau de la base |
| F3 | Disponibilité | 99 % en heures ouvrées | Atteint, mesuré | Sonde externe chaque minute, rapport mensuel de disponibilité, alerte en 2 minutes, retour automatique à la version précédente en cas de déploiement défaillant |
| F4 | Traçabilité | 12 mois d'historique | Atteint | Journal des consultations (qui a lu quel dossier, compte rendu ou fichier), journaux métier, journal applicatif ; conservation 12 mois |
| F5 | Reprise | Perte max. 24 h, reprise en 4 h | Atteint | Sauvegarde quotidienne chiffrée, copiée hors du serveur avec versionnage, alerte en cas d'échec, procédure écrite, test de restauration réalisé |
| F6 | Détection | Signalement en 24 h | Atteint | Alertes immédiates sur indisponibilité, tentatives répétées, erreurs serveur, échec de sauvegarde et modification après signature, adressées aux administrateurs du laboratoire et au prestataire |

### 1.4 Éléments budgétaires

- Le TdR prévoyait une enveloppe de 1 150 000 pour renforcer la plateforme existante. L'audit a montré que cette plateforme devait être remplacée ; l'effort supplémentaire a été absorbé dans le cadre de la prestation (section 4.2).
- La nouvelle architecture entraîne des **coûts récurrents** plus élevés que l'ancien hébergement : le forfait d'hébergement et d'exploitation passe de 300 000 à 523 750 FCFA par an (serveur dédié incluant le relais d'appels, stockage S3, supervision, administration). S'y ajoutent le contrat de maintenance de l'application (1 948 500 FCFA par an) et les modules optionnels. Le détail figure en section 7. Sans maintenance régulière, la nouvelle plateforme se retrouvera dans la situation de l'ancienne au bout de quelques semaines ou mois.

### 1.5 Points soumis à la décision de la direction

1. Valider les coûts récurrents d'exploitation et les modules optionnels retenus (section 7).
2. Mettre en œuvre les recommandations organisationnelles de la section 8.2.
3. Valider le calendrier de maintien dans le temps (section 9).

---

## 2. Contexte, périmètre et méthodologie

### 2.1 Contexte de la mission

Le CAAP, laboratoire d'anatomie et de cytologie pathologiques à Cotonou, utilise depuis 2022 une plateforme développée par KAWA SERVICES, qui couvre toute son activité : patients, médecins prescripteurs, demandes d'examen, comptes rendus, consultations, facturation, caisse, personnel. Dix personnes l'utilisent sur site, des médecins en France et le comptable y accèdent à distance.

Le TdR fixait cinq objectifs : connaître l'état de sécurité, corriger les faiblesses graves, remettre la plateforme sur des bases maintenues, protéger les données contre la perte, la fuite et la modification non autorisée, et pouvoir reprendre l'activité rapidement après un incident. Il rappelle que les données traitées sont des données de santé au sens du Code du numérique (loi n° 2017-20, modifiée par la loi n° 2020-35, Livre V).

### 2.2 Périmètre de la prestation

- **L'application** : audit de l'ancienne plateforme, conception et développement de la nouvelle, dans toutes ses fonctions ; audit et correction de la nouvelle.
- **Les données** : reprise intégrale des données existantes, sécurisation du stockage, sauvegardes et test de restauration.
- **L'hébergement** : architecture de déploiement, configuration des serveurs applicatifs, chaîne de mise en production, supervision.
- **Les utilisateurs** : règles de connexion, droits d'accès par rôle, journal des accès, outils de revue des comptes.
- **L'organisation** : recommandations de procédures internes (section 8.2) et dispositif de maintien en condition de sécurité (section 9).

### 2.3 Référentiels et méthodologie d'audit

| Rôle | Référentiel |
|---|---|
| Cadre légal | Code du numérique, Livre V ; exigences de l'APDP |
| Mesures de sécurité | ISO/IEC 27001:2022 et 27002:2022 |
| Lecture pour la direction | NIST CSF 2.0 |
| Analyse de risques | ISO/IEC 27005:2022, version simplifiée |
| Sécurité applicative | OWASP ASVS 4.0.3 niveau 2 ; OWASP Top 10 2021 |
| Cotation des vulnérabilités | CVSS v3.1 : Critique ≥ 9,0 ; Élevée 7,0–8,9 ; Moyenne 4,0–6,9 ; Faible < 4,0 |
| Méthode d'audit | ISO 19011 ; NIST SP 800-115 |
| Sauvegarde et reprise | ISO 22301 (RPO, RTO) |
| Données de santé | ISO/IEC 27701 ; ISO 15189:2022 §7.11 |

**Méthodologie.** L'audit a été conduit par revue statique du code source des trois dépôts (ancienne plateforme, API et interface web de la nouvelle), de leur configuration et de leur historique de versions, complétée par des tests automatisés d'intégration sur la nouvelle plateforme et par des tests en production (en-têtes, restauration, alertes). Aucune donnée réelle n'est sortie de l'hébergement du laboratoire. Les secrets rencontrés sont cités par leur emplacement, jamais par leur valeur.

---

## 3. État des lieux initial de la plateforme existante

L'ancienne plateforme est une application Laravel 8 (PHP) avec une base MySQL/MariaDB, hébergée sur un serveur cPanel. Développée par KAWA SERVICES et mise en service en 2022, elle a été construite sur les composants de référence de l'époque, dans le cadre budgétaire et d'hébergement alors retenu, pour un outil interne à effectif réduit. Elle n'a pas fait l'objet d'un contrat de maintenance par la suite ; son dépôt compte 1 019 commits, du 5 avril 2022 au 1er septembre 2025.

L'état des lieux ci-dessous mesure l'écart entre cette plateforme et les exigences de 2026 : référentiels de sécurité actualisés (OWASP ASVS et Top 10 2021, ISO 27002:2022), attention accrue de l'APDP aux données de santé, et besoins du laboratoire qui ont évolué (accès distants, application mobile, traçabilité). La majorité des constats relève de trois causes : la fin de support des composants, l'absence de mises à jour depuis la mise en service, et des exigences qui ne faisaient pas partie du périmètre de 2022 (journal des accès, immutabilité des comptes rendus validés, cloisonnement renforcé).

### 3.1 Inventaire des composants et statut de support

| Composant | Version constatée | Fin du support sécurité | Statut |
|---|---|---|---|
| PHP (production) | 8.1 (gestionnaire cPanel `ea-php81`) | 31/12/2025 | **Obsolète** |
| Laravel Framework | 8.83.27 | 24/01/2023 | **Obsolète** ; exposé à CVE-2024-52301 |
| laravel/sanctum (API mobile) | 2.15.1 | lié à Laravel 8 | Obsolète |
| yajra/laravel-datatables | 9.21.2, contrainte `*` | lié à Laravel 8 | Obsolète, version non maîtrisée |
| Paquets CORS (deux installés) | 3.0.0 | abandonnés | Obsolètes et redondants |
| TCPDF (génération PDF) | 6.7.7 | — | **Vulnérable** : CVE-2024-56519, -56522, -56527 |
| dompdf | 2.0.4 | branche 3.x courante | Obsolète |
| Symfony (composants HTTP) | 5.4.38 | branche LTS jusqu'en 02/2029 | En retard : CVE-2024-50340, -50345 |
| composer.phar versionné | 2.5.5 | — | Vulnérable : CVE-2024-24821, -35241, -35242 |
| MySQL / MariaDB | 10.5 | 06/2025 | **Obsolète** |
| jQuery (effectivement chargé) | 1.12.4 | branche 1.x abandonnée | CVE-2015-9251, 2019-11358, 2020-11022, 2020-11023 |
| jQuery UI | 1.12.1 | — | CVE-2021-41182/41183/41184, CVE-2022-31160 |
| Bootstrap (CDN) | 4.5.2 | 01/01/2023 | **Fin de vie** |
| DataTables | 1.10.25 | — | CVE-2021-23445 |
| Chart.js | 2.9.3 | — | CVE-2020-7746 |
| CKEditor 5 (CDN) | 35.1.0 et 36.0.0 | — | Obsolète |
| SweetAlert2 (CDN) | 11.10.5 | — | Versions ≥ 11.4.9 contiennent un « protestware » |
| toastr (CDN) | `latest`, non épinglé | — | Version non maîtrisée |
| axios | 0.21.4 | — | CVE-2023-45857, CVE-2025-27152 |
| Intégrité des 20 inclusions CDN (SRI) | absente | — | Aucune protection si un CDN est compromis |

**Synthèse.** Quatre ans après la mise en service, sans mise à jour, le socle (PHP 8.1, Laravel 8 et ses paquets) ne recevait plus de correctifs de sécurité et plusieurs bibliothèques avaient des vulnérabilités publiques corrigées dans des versions postérieures à 2022. C'est le cycle de vie normal de composants non maintenus : le critère « tous les composants supportés » du TdR ne pouvait plus être rempli sans intervention.

### 3.2 Analyse des vulnérabilités applicatives

Vingt-et-une vulnérabilités ont été relevées et cotées. Le registre complet figure en fin de section. Les plus importantes sont résumées ici.

**Exposition directe de données de santé**

- **V-04 – Sauvegardes complètes de la base dans un répertoire public** (Élevée, CVSS 7,5). La sauvegarde quotidienne de 18 h 30 écrivait un export SQL complet dans un répertoire servi par le site web, sous un nom construit à partir de la date. Toute personne devinant le nom pouvait télécharger l'intégralité des données : patients, comptes rendus, factures, empreintes de mots de passe. L'export n'était ni chiffré, ni copié hors du serveur, ni soumis à rotation.
- **V-05 – PDF des comptes rendus accessibles publiquement** (Élevée, CVSS 7,5). Chaque compte rendu généré était copié dans un répertoire public sous un nom égal à l'heure de génération, facile à énumérer.
- **V-14 – Signatures manuscrites des pathologistes exposées** (Moyenne, CVSS 6,5). Une archive versionnée dans le répertoire public contenait les images de signature des médecins.

**Secrets et comptes**

- **V-01 – Secrets de production dans le dépôt de code** (Élevée, CVSS 8,5). Un fichier de configuration versionné depuis mai 2023 contenait la clé de l'application, les identifiants de la base de données, le mot de passe de la messagerie et des clés de services de paiement en mode réel. D'autres secrets étaient écrits en dur dans le code. Ces valeurs restent dans l'historique du dépôt même après suppression : elles ont toutes été changées (section 6.1).
- **V-02 – Paramètres et secrets de l'application lisibles et modifiables par tout utilisateur** (Élevée, CVSS 8,1). L'écran des paramètres ne vérifiait aucune permission : n'importe quel compte pouvait lire le mot de passe de messagerie et les jetons des services tiers, ou rediriger les appels vocaux vers un serveur de son choix.
- **V-03 – Mot de passe initial identique pour les nouveaux comptes** (Élevée, CVSS 8,1). Le changement n'était pas imposé à la première connexion et aucune règle de complexité n'était appliquée, le changement reposant sur la discipline de chaque utilisateur.
- **V-06 – Double authentification contournable** (Élevée, CVSS 7,4). Le contrôle n'était appliqué qu'à la page d'accueil ; les 430 autres pages ne demandaient que le mot de passe. Le code était généré sans aléa cryptographique, sans expiration ni limite d'essais, et une valeur fixe était acceptée dans un cas courant.
- **V-12 – Aucune limite de tentatives de connexion** (Moyenne, CVSS 6,5).

**Contrôle d'accès**

- **V-09 – Accès aux dossiers d'autres patients et aux factures par simple changement d'identifiant** (Élevée, CVSS 7,1). Le contrôle des droits était codé à la main, méthode par méthode ; 214 contrôles pour 543 méthodes, certains mis en commentaire. Le dossier patient complet, les factures et le suivi des comptes rendus n'en avaient aucun.
- **V-10 – API mobile sans expiration de session, sans double authentification et sans contrôle de rôle** (Élevée, CVSS 7,1). Un médecin pouvait obtenir les demandes et comptes rendus de tous les patients.
- **V-13 – Points d'API accessibles sans authentification** (Moyenne, CVSS 6,5).
- **V-15 – Protection anti-CSRF affaiblie** (Moyenne, CVSS 5,4) : 47 actions de suppression déclenchables par un simple lien.

**Intégrité et traçabilité**

- **V-11 – Compte rendu validé modifiable sans trace, signataires choisis librement** (Moyenne en CVSS 6,5, **élevée en impact métier**). Un compte rendu signé pouvait être réécrit, repassé en brouillon, ou « signé » au nom de n'importe quel pathologiste. La journalisation de ces modifications n'était pas effective et aucune version antérieure n'était conservée : l'immutabilité des comptes rendus validés ne faisait pas partie des exigences de 2022.
- **V-17 – Aucune trace des consultations, suppressions physiques** (Moyenne, CVSS 5,4). Patients, factures et utilisateurs étaient supprimés définitivement.

**Exécution de code et injection**

- **V-08 – Téléversement de fichier avec extension libre dans un répertoire exécutable** (Élevée, CVSS 7,2). Un fichier PHP déposé comme « signature » aurait été exécuté par le serveur, ce qui donne un contrôle total de celui-ci.
- **V-16 – Injection de script via la signature de retrait** (Moyenne, CVSS 5,4).

**Configuration**

- **V-07 – Composants obsolètes** (Élevée, CVSS 7,4), voir 3.1.
- **V-18 – Mode débogage actif, journalisation de données personnelles** (Moyenne, CVSS 5,3).
- **V-19 – Restriction horaire de connexion inopérante** (Moyenne, CVSS 4,3).
- **V-20 – En-têtes de sécurité absents, CORS ouvert à toutes les origines** (Moyenne, CVSS 4,2).
- **V-21 – Affectation de masse non restreinte sur 70 modèles sur 79** (Faible, CVSS 3,1).

**Points contrôlés sans constat** : pas d'injection SQL constatée, mots de passe hachés avec bcrypt, inscription publique désactivée, jeton de réinitialisation expirant en 60 minutes.

**Synthèse quantitative** : Critique 0 · Élevée 10 · Moyenne 10 · Faible 1 · **Total 21**.

Aucune vulnérabilité n'atteint 9,0 en CVSS strict, parce que chacune, prise isolément, suppose une condition (accès au dépôt, compte authentifié, lien public actif). En revanche, **V-01, V-04, V-05 et V-11 sont critiques en impact métier** : elles portent sur des données de santé et sur l'intégrité de diagnostics signés. Enchaînées, V-03, V-12, V-06 puis V-08 mènent à une compromission complète du serveur.

**Registre des vulnérabilités de la plateforme existante**

| ID | Titre | OWASP 2021 | CVSS | Sévérité |
|---|---|---|---|---|
| V-01 | Secrets de production dans le dépôt | A07 | 8,5 | Élevée |
| V-02 | Paramètres et secrets accessibles à tout utilisateur | A01 | 8,1 | Élevée |
| V-03 | Mot de passe par défaut, aucune politique | A07 | 8,1 | Élevée |
| V-04 | Sauvegardes SQL dans le stockage public | A05 | 7,5 | Élevée |
| V-05 | PDF de comptes rendus publics | A01 | 7,5 | Élevée |
| V-06 | 2FA contournable, code faible | A07 | 7,4 | Élevée |
| V-07 | Composants obsolètes et vulnérables | A06 | 7,4 | Élevée |
| V-08 | Téléversement exécutable | A04 | 7,2 | Élevée |
| V-09 | IDOR, absence d'autorisation | A01 | 7,1 | Élevée |
| V-10 | API mobile sans contrôle | A01/A07 | 7,1 | Élevée |
| V-11 | Compte rendu validé modifiable sans trace | A04/A09 | 6,5 | Moyenne |
| V-12 | Aucune limite de tentatives | A07 | 6,5 | Moyenne |
| V-13 | API publique non authentifiée | A01/A05 | 6,5 | Moyenne |
| V-14 | Signatures des pathologistes exposées | A01 | 6,5 | Moyenne |
| V-15 | CSRF affaibli, suppressions en GET | A01 | 5,4 | Moyenne |
| V-16 | Injection de script (signature SVG) | A03 | 5,4 | Moyenne |
| V-17 | Pas d'audit des accès, suppressions physiques | A09 | 5,4 | Moyenne |
| V-18 | Mode débogage, journalisation de données personnelles | A05 | 5,3 | Moyenne |
| V-19 | Restriction horaire inopérante | A04 | 4,3 | Moyenne |
| V-20 | En-têtes absents, CORS ouvert | A05 | 4,2 | Moyenne |
| V-21 | Affectation de masse | A08 | 3,1 | Faible |

### 3.3 Infrastructure d'hébergement, données et sauvegardes

- **Hébergement** : serveur cPanel/Apache, domaine `gestion.caap.bj`. Le serveur de messagerie référencé suggère un hébergeur dont l'infrastructure est au Canada. Si c'est le cas, des données de santé étaient hébergées hors du Bénin, ce qui relève des règles de transfert du Livre V du Code du numérique.
- **Arborescence** : la racine du projet était la racine du site web, avec une réécriture vers le sous-répertoire `public`.
- **Déploiement** : manuel, probablement par FTP ; aucune chaîne automatisée, aucun test exécuté avant mise en ligne.
- **Base de données** : MySQL/MariaDB sur le même serveur, un seul utilisateur aux pleins droits, sans chiffrement au repos.
- **Fichiers médicaux** : tous sur le disque local du serveur, dans un répertoire exposé publiquement (V-04, V-05, V-14).
- **Sauvegardes** : un seul export SQL quotidien, sur le même serveur, dans un répertoire public, sans chiffrement, sans rotation, sans copie externe, sans sauvegarde des fichiers et sans test de restauration. Un échec de l'export restait silencieux.
- **Services tiers** : facturation normalisée e-MECeF (DGI), service d'appels et de SMS, plusieurs passerelles de paiement.

### 3.4 Conclusion de l'état des lieux

Une correction incrémentale de l'ancienne plateforme n'était pas la voie la plus sûre ni la plus économique, pour six raisons :

1. **Le socle est hors support.** Revenir sur un socle maintenu suppose quatre montées de version majeures de Laravel (9 à 12), une version récente de PHP et le remplacement de la plupart des paquets. Cela revient à reprendre l'essentiel du code.
2. **Une couverture de tests limitée.** La plateforme de 2022 avait été livrée sans budget de tests automatisés ni contrat de maintenance ; une remise à niveau de cette ampleur ne pouvait pas être validée sans risque de régression sur des données de santé.
3. **Un contrôle d'accès à centraliser.** Conçu pour un outil interne à effectif réduit, il est appliqué méthode par méthode ; les exigences actuelles (accès distants, médecins externes, application mobile) imposent une règle centrale.
4. **Une traçabilité à concevoir.** Le journal des consultations, le versionnement des comptes rendus et la conservation des données supprimées ne faisaient pas partie du périmètre de 2022 ; ils ne s'ajoutent pas à un modèle existant sans le restructurer.
5. **Un stockage de fichiers à reprendre.** L'hébergement cPanel retenu en 2022 n'offrait ni stockage objet ni isolation ; les flux de fichiers devaient être repensés avec un hébergement adapté.
6. **Des secrets de configuration présents dans l'historique du dépôt.** Quel que soit le scénario, leur rotation complète s'imposait.

Le coût et le risque d'une remise à niveau étaient comparables à ceux d'une réécriture, sans en apporter les garanties. La refonte a permis de repartir sur un socle maintenu, avec les exigences de 2026 intégrées dès la conception, et de les préserver dans le temps par le contrat de maintenance (section 7.3).

---

## 4. Analyse des options et décision de refonte

### 4.1 Options évaluées

| Option | Description | Évaluation |
|---|---|---|
| A. Corriger l'existant | Traiter les 21 vulnérabilités une à une sur Laravel 8 | Le socle reste hors support : le critère « composants supportés » du TdR ne peut pas être atteint. Chaque correction se fait sans tests, sur du code à risque. |
| B. Migrer le framework | Monter Laravel 8 vers 12 et PHP vers 8.3, puis corriger | Quatre montées majeures, remplacement de la majorité des paquets et des bibliothèques front. L'autorisation, la traçabilité et le stockage restent à refaire. Effort proche de C, sans ses bénéfices. |
| C. Refondre | Nouvelle plateforme sur des composants récents, reprise intégrale des données | Permet une autorisation centralisée, un audit dès la conception, un stockage privé et chiffré, des tests automatisés. Seule option compatible avec les cinq objectifs du TdR. **Retenue.** |

### 4.2 Incidence budgétaire

Le TdR prévoyait une enveloppe de 1 150 000 pour le renforcement de la plateforme existante. Le choix de la refonte, rendu nécessaire par l'état de la plateforme (section 3.4), a représenté un effort de développement supérieur à cette enveloppe. Cet écart a été absorbé dans le cadre de la prestation, sans modification du montant convenu.

Le laboratoire bénéficie en outre d'améliorations qui n'étaient pas demandées par le TdR : interface entièrement refaite, appels par internet, notifications vocales en langue locale, SMS, facturation normalisée intégrée, gains de performance (section 5). L'application mobile relève d'une prestation distincte (2025–2026) ; la présente plateforme en fournit le socle technique.

---

## 5. Description de la plateforme mise à niveau

### 5.1 Architecture technique et composants

La nouvelle plateforme sépare l'interface web (labsys_front) de l'API métier (labsys_api). Les deux sont déployées en conteneurs Docker sur un serveur dédié, derrière un serveur web nginx qui termine le chiffrement TLS (TLS 1.2 minimum, configuration versionnée). L'API n'est pas exposée directement sur internet.

**API (labsys_api)**

| Composant | Version | Support éditeur |
|---|---|---|
| Java | 21 (LTS) | au moins jusqu'en 2029 |
| Spring Boot | 4.x, dernière branche publiée | support actif, suivi par l'analyse automatique des dépendances |
| PostgreSQL | 16 | 09/11/2028 |
| Migrations de schéma | Flyway, schéma vérifié au démarrage | — |
| Jetons de session | jjwt 0.12.x | maintenu |
| Double authentification | code par e-mail ou application (TOTP), secret chiffré en base | — |
| Limitation de débit et verrouillage | bucket4j 8.10.x + compteur par compte | maintenu |
| Génération PDF | Bibliothèque maintenue, remplacée lors de la mise à niveau | support actif |
| Stockage S3 | AWS SDK v2, fichiers chiffrés AES-256-GCM avant envoi | maintenu |
| Notifications mobiles | Firebase | maintenu |
| SMS | FluidPay (charge utile chiffrée) | — |
| Appels vocaux | FluidPay, message audio en fon choisi selon la langue du patient | — |
| Facturation normalisée | FluidInvoice (e-MCF / DGI), module optionnel | — |
| Appels par internet (mobile) | WebRTC, signalisation WebSocket, relais coturn avec identifiants temporaires | — |
| Conteneur | eclipse-temurin 21 JRE, tag épinglé, utilisateur non administrateur | — |

**Interface web (labsys_front)** – 86 écrans.

| Composant | Version | Support éditeur |
|---|---|---|
| Next.js | 16.2.x | maintenu |
| React | 19.2.x | maintenu |
| TypeScript | 5.9.x | maintenu |
| Tailwind CSS | 4.3.x | maintenu |
| Radix UI, TanStack Query et Table, Zustand, react-hook-form, zod, DOMPurify | versions courantes | maintenus |
| Conteneur et chaîne d'intégration | Node 22 (LTS) | maintenu |

**Maintien en condition de sécurité.** Une analyse automatique des dépendances (OWASP Dependency-Check côté API, `npm audit` côté interface) s'exécute à chaque déploiement et le bloque si une vulnérabilité de score ≥ 7 est connue. Dependabot propose chaque semaine les mises à jour. Le critère « tous les composants supportés » est ainsi vérifié en continu, et pas seulement à la date de ce rapport.

### 5.2 Reprise des données et migration vers PostgreSQL

**Méthodologie.** La base MariaDB 10.5 de l'ancienne plateforme (85 tables, 852 colonnes) a été convertie vers un schéma PostgreSQL 16 (79 tables, 980 colonnes). 

**Contrôles de complétude.** Vérification par agrégats métier (totaux de caisse, nombre de comptes rendus par statut), contrôle d'intégrité référentielle après chargement, absence de texte mal encodé sur 51 447 lignes. Le schéma est vérifié à chaque démarrage de l'API.

**Écarts résiduels connus**, documentés et suivis : 24 sessions de caisse jamais fermées dans l'ancienne plateforme, 8 815 opérations de caisse sans facture associée (héritage de l'ancien modèle). Un script de contrôle permet de les lister.

### 5.3 Stockage sécurisé des fichiers

Les fichiers (pièces d'examen, PDF de comptes rendus, documents RH, justificatifs) ne sont plus dans un répertoire public du serveur web.

- Ils sont **chiffrés avant envoi** (AES-256-GCM, clé enveloppée) puis stockés sur S3 avec chiffrement côté serveur et versionnage du bucket.
- **Aucune adresse publique ni pré-signée** n'est générée : tout téléchargement passe par l'API, qui vérifie la session, le site et le droit de l'utilisateur sur le dossier auquel le fichier est rattaché.
- Le serveur garde une copie locale de secours, elle-même sauvegardée.

### 5.4 Refonte de l'interface utilisateur

L'interface a été entièrement réécrite.

| | Ancienne interface | Nouvelle interface |
|---|---|---|
| Technologie | 237 vues rendues côté serveur, jQuery, Bootstrap 4, thème acheté | Application React/Next.js typée, design system maison (39 composants) |
| Ressources externes | 69 scripts chargés depuis des sites tiers, sans contrôle d'intégrité | **Aucune ressource tierce** : polices et scripts auto-hébergés, politique CSP bloquante |
| Échappement des données | 47 affichages non échappés | Échappement automatique par React, nettoyage DOMPurify des rares contenus HTML (signatures, éditeur) |
| Navigation | menus hétérogènes selon les écrans | Menu latéral unique, filtré selon les droits de l'utilisateur, organisé par métier : tableau de bord, examens, demandes, comptes rendus, comptabilité, administration, équipes, documentation |
| Mobile | non prévue | Menu en panneau sur petit écran, mise en page adaptée, version imprimable |
| Accessibilité | non traitée | Libellés d'accessibilité (99), alertes annoncées, animations réduites sur demande, palette de graphiques vérifiée pour le daltonisme |
| Fichiers | servis directement depuis le disque public | Servis par l'API après contrôle de droit, aperçus chargés en mémoire |

**Écrans par module** : patients (liste paginée, fiche) ; demandes d'examen (création, prise en charge, macroscopie, affectations, étiquettes, immunohistochimie, espace personnel) ; comptes rendus (éditeur, signataires, modèles, historique, versions, suivi, bandeau « modifié après signature ») ; consultations et rendez-vous ; facturation et caisse (factures, ventes, dépenses, bons de caisse, ouverture et fermeture, remboursements, contrats) ; personnel (employés, paie, congés) ; administration (utilisateurs, rôles, permissions, accès mobile, journal d'accès) ; profil avec double authentification.

**Parcours utilisateur sécurisés** : choix du site obligatoire après connexion ; saisie du code à usage unique sur un écran dédié ; déconnexion automatique après 15 minutes d'inactivité avec préavis d'une minute, synchronisée entre les onglets, mémoire du navigateur vidée à la déconnexion ; confirmation avant toute suppression (42 écrans) ; indicateur de force du mot de passe.

### 5.5 Mesures de sécurité mises en œuvre

Les références ISO 27002:2022 et OWASP ASVS 4.0.3 sont données à titre de correspondance.

**F1 – Confidentialité**

| Mesure | ISO 27002 | ASVS |
|---|---|---|
| Session par jetons en cookies HttpOnly, Secure, SameSite=Strict : le navigateur ne peut pas les lire | 8.5 | V3.4 |
| Mots de passe hachés BCrypt ; politique : 12 caractères minimum, refus des mots de passe courants et de ceux contenant le nom ou l'e-mail | 5.17, 8.5 | V2.1, V2.4 |
| Code à usage unique obligatoire à chaque connexion (e-mail ou application) ; secret d'application chiffré en base | 8.5 | V2.7, V2.8 |
| Limitation des essais sur la connexion, le code à usage unique, la réinitialisation et le renvoi de code (5 par minute, 20 par heure) ; verrouillage du compte 15 minutes après 10 échecs ; adresse IP lue uniquement depuis nginx | 8.5, 8.6 | V2.2.1, V11.1.4 |
| Réinitialisation du mot de passe par lien envoyé par e-mail, valable 1 h, à usage unique, jeton stocké haché | 5.17 | V2.5 |
| Jeton d'accès de 5 minutes, session fermée après 30 minutes d'inactivité (60 pour les médecins), rotation des jetons et liste de révocation | 8.5 | V3.3 |
| Rôles et permissions : plus de 110 permissions distinctes vérifiées par l'API sur chaque route, tableau de bord inclus | 5.15, 8.3 | V4.1 |
| Cloisonnement par site vérifié à chaque requête ; contrôle par dossier : un médecin ne voit que ses demandes et comptes rendus | 8.3 | V4.2.1 |
| Fichiers rattachés à leur dossier et servis uniquement après le même contrôle de droit que le dossier ; protection contre la traversée de chemin | 8.3 | V4.2.1, V12.5 |
| Chiffrement des fichiers au repos (AES-256-GCM) avant envoi sur S3 | 8.24 | V6.2 |
| Application mobile : code PIN verrouillé après 5 échecs, appareils révocables individuellement, code d'enrôlement à usage unique valable 15 minutes, actions journalisées | 8.5 | V2.2.1, V3.5 |
| En-têtes de sécurité sur l'API et sur l'interface : HSTS 1 an, CSP bloquante avec nonce, refus d'affichage en cadre, nosniff, Referrer-Policy, Permissions-Policy | 8.26 | V14.4 |
| Origines autorisées (CORS) listées explicitement | 8.26 | V14.5.3 |
| Secrets hors du dépôt, variables obligatoires au démarrage, aucun secret ni donnée dans l'historique | 5.17, 8.24 | V2.10, V6.4 |
| Lien public de facture : jeton aléatoire avec expiration | 5.15 | V3.5 |

**F2 – Intégrité**

| Mesure | ISO 27002 | ASVS |
|---|---|---|
| Avant toute modification d'un compte rendu validé ou livré, la version complète précédente est conservée ; les versions sont consultables et comparables à l'écran | 8.13, 8.32 | V7.3 |
| Toute modification après signature exige un motif, est tracée (auteur, date, champs, motif) et signalée par e-mail aux administrateurs ; un bandeau l'affiche à l'écran | 8.15 | V7.2 |
| Une seule route de modification, porteuse de la trace ; la route de création refuse de modifier | 8.15 | V7.2.2 |
| Validation et modification sont des permissions distinctes | 8.3 | V4.1 |
| Journaux (comptes rendus, accès, versions) en ajout seul : l'utilisateur de base de données de l'application n'a pas le droit de les modifier ni de les supprimer | 8.15 | V7.3.3 |
| Suppression logique (conservation avec marqueur) des comptes rendus, patients et demandes ; suppressions journalisées | 8.10 | — |
| Validation des entrées, nettoyage du HTML à l'enregistrement et avant génération PDF | 8.28 | V5.1, V5.2 |
| Transactions sur toutes les opérations composées ; schéma vérifié au démarrage | 8.28, 8.32 | — |

**F3 – Disponibilité**

| Mesure | ISO 27002 |
|---|---|
| Sondes de santé de la base et de l'API, redémarrage automatique des conteneurs | 8.14 |
| Sonde externe chaque minute sur l'API et l'interface ; alerte après deux échecs ; rapport mensuel de disponibilité en heures ouvrées | 8.16 |
| Déploiement vérifié : retour automatique à la version précédente si le nouveau conteneur n'est pas sain en deux minutes | 8.32 |
| Alerte sur espace disque et sur erreurs serveur répétées | 8.16 |
| Gestion centralisée des erreurs : aucune information technique renvoyée à l'utilisateur | 8.28 |

**F4 – Traçabilité**

| Mesure | ISO 27002 |
|---|---|
| Journal des **consultations** : qui a lu, téléchargé ou imprimé quel dossier patient, demande, compte rendu, fichier ou facture, quand, depuis quelle adresse ; consultable par les administrateurs, exportable | 8.15 |
| Auteur et date de création et de modification sur toutes les entités | 8.15 |
| Journal des comptes rendus : création, modification, validation, livraison, suppression, impression, SMS, appel | 8.15 |
| Journal des actions de l'application mobile, des appels, des affectations, des factures, des remboursements | 8.15 |
| Conservation : **12 mois** pour les journaux en base (purge mensuelle au-delà) et pour le journal applicatif (fichier persistant hors conteneur) | 8.15 |

**F5 – Reprise après incident**

| Mesure | ISO 27002 / 22301 |
|---|---|
| Export complet de la base chaque jour à 18 h 30 (heure de Cotonou), **chiffré** avec une clé dont la partie privée n'est pas sur le serveur | 8.13, 8.24 |
| Copie quotidienne vers un bucket S3 distinct, versionné, en écriture seule pour le serveur (30 versions quotidiennes, 12 mensuelles) ; copie quotidienne des fichiers | 8.13 |
| Alerte en cas d'échec de l'export ou de la copie, et contrôle indépendant chaque soir de la présence de la sauvegarde du jour | 8.16 |
| Procédure de restauration documenter et exécutable | 5.30 |

**F6 – Détection**

| Mesure | ISO 27002 |
|---|---|
| Échecs de connexion, refus d'accès, dépassements de limite et verrouillages journalisés | 8.15 |
| Alerte immédiate : plus de 20 échecs de connexion en 10 minutes, ou plus de 5 verrouillages en 1 heure | 8.16 |
| Alerte immédiate en cas de modification d'un compte rendu après signature | 8.16 |
| Alertes d'indisponibilité, d'erreurs serveur, d'échec de sauvegarde, d'espace disque | 8.16 |
| Métriques conservées 90 jours (disponibilité, temps de réponse, erreurs), accessibles sur le réseau interne uniquement | 8.16 |

### 5.6 Performances

La nouvelle plateforme est sensiblement plus rapide à l'usage. Les facteurs techniques sont :

- API compilée (Java) au lieu d'un script interprété à chaque requête ;
- PostgreSQL 16 avec un schéma indexé pour les recherches de demandes et de patients ;
- pagination côté serveur sur 27 écrans de liste, recherche temporisée ;
- mise en cache des données côté navigateur (30 secondes), rechargement seulement si nécessaire ;
- plus aucune ressource chargée depuis des sites tiers ; une seule police variable auto-hébergée ;
- ouverture d'un dossier en un seul appel pour l'application mobile, index compact pour le travail hors ligne.

### 5.7 Périmètre fonctionnel

**Fonctionnalités conservées** (toutes les fonctions de l'ancienne plateforme, §4.3 du TdR) : patients, médecins et établissements, catalogue d'examens, demandes d'examen et affectations, comptes rendus avec modèles et signatures, consultations et rendez-vous, facturation, paiements, Mobile Money, caisse, dépenses, banque, remboursements, facturation normalisée e-MECeF, contrats, personnel (employés, contrats, paie, congés, documents), stock, gestion documentaire, support et tickets, messagerie, tableau de bord, rôles et paramètres.

**Fonctionnalités nouvelles** :

| Fonction | Description |
|---|---|
| Appels par internet | Appels audio entre l'application mobile et le laboratoire (WebRTC), portés par l'API et un serveur de relais dédié |
| Socle de l'application mobile (prestation distincte, 2025–2026) | Services d'enrôlement par code, PIN, appareils révocables, signature de validation par appareil, index des demandes pour le travail hors ligne |
| Notifications vocales en langue locale | Appel automatique du patient avec un message en fon quand son compte rendu est disponible, avec reprise automatique des appels hors plage horaire |
| SMS | Avis au patient avec lien sécurisé de téléchargement de sa facture |
| Facturation normalisée intégrée | Module FluidInvoice (e-MCF / DGI), optionnel |
| Discussions par dossier | Échanges internes rattachés à une demande d'examen |
| Dépôts bancaires et bons de caisse | Suivi de la caisse jusqu'au dépôt en banque |
| Historique des affectations | Qui a pris en charge quelle demande, et quand |
| Versions des comptes rendus | Chaque état d'un compte rendu signé est conservé et comparable |
| Journal d'accès | Qui a consulté quel dossier, consultable par les administrateurs |
| Stockage chiffré sur S3 | Voir 5.3 |
| Double authentification obligatoire | Voir 5.5 |

---

## 6. Vérification de l'efficacité des mesures

### 6.1 Vérification du traitement des vulnérabilités initiales

Pour chaque vulnérabilité de la section 3, l'état de la nouvelle plateforme a été vérifié par revue du code, puis par test automatisé ou test en production.

| ID | Vulnérabilité initiale | Nouvelle plateforme | Vérification |
|---|---|---|---|
| V-01 | Secrets dans le dépôt | Secrets hors du dépôt, aucun dans l'historique ; anciens secrets changés | Analyse de l'historique ; liste des rotations |
| V-02 | Paramètres modifiables par tous | Permissions vérifiées par l'API sur chaque route | Test : 403 sans permission |
| V-03 | Mot de passe par défaut | Politique de 12 caractères avec liste d'exclusion, code à usage unique obligatoire | Test : mots de passe faibles refusés |
| V-04 | Sauvegardes publiques | Chiffrées, hors du site web, copiées hors du serveur | Objet chiffré sur S3 chaque jour |
| V-05 | PDF publics | Servis après contrôle de droit sur le dossier | Test : 403 hors droit |
| V-06 | 2FA contournable | Obligatoire sur toute l'application, essais limités, verrouillage | Test : 429 au 6ᵉ essai, verrouillage au 10ᵉ |
| V-07 | Composants obsolètes | Socle supporté, analyse des dépendances bloquante | Rapport Dependency-Check sans vulnérabilité ≥ 7 |
| V-08 | Téléversement exécutable | Fichiers hors du serveur web, chiffrés, servis par l'API | Revue ; test d'altération refusée |
| V-09 | Accès aux données d'autrui | Cloisonnement par site et par dossier | Test : médecin → seulement ses demandes |
| V-10 | API mobile sans contrôle | PIN, verrouillage, appareils révocables, journal | Tests d'intégration mobile |
| V-11 | Compte rendu validé modifiable sans trace | Version conservée, motif obligatoire, trace, alerte, journaux en ajout seul | Test : version créée, `UPDATE` refusé par PostgreSQL |
| V-12 | Aucune limite de tentatives | Limites sur toutes les routes d'authentification | Test : 429 |
| V-13 | API publique non authentifiée | Seul le lien de facture, à jeton aléatoire expirant | Revue des routes publiques |
| V-14 | Signatures exposées | Fichiers servis par l'API après contrôle de droit | Test : 403 hors droit |
| V-15 | CSRF, suppressions en GET | Cookies SameSite=Strict, API REST | Revue |
| V-16 | Injection de script (signature SVG) | Nettoyage à l'enregistrement (API) et à l'affichage (DOMPurify), CSP bloquante | Test : `onload` supprimé |
| V-17 | Pas d'audit des consultations, suppressions physiques | Journal d'accès, suppression logique et journalisée | Test : ligne READ à l'ouverture |
| V-18 | Mode débogage | Erreurs génériques, documentation d'API désactivée en production | Revue |
| V-19 | Restriction horaire inopérante | Fonction non reprise | — |
| V-20 | En-têtes absents, CORS ouvert | En-têtes complets sur l'API et l'interface, CORS explicite | Observatoire Mozilla : note A |
| V-21 | Affectation de masse | Objets typés et validés | Revue |

Bilan : **21 vulnérabilités fermées sur 21**.

### 6.2 Audit de la plateforme mise à niveau et actions correctives

La nouvelle plateforme a été auditée avec le même référentiel. Seize points ont été relevés, corrigés et vérifiés.

| ID | Constat initial | Sévérité | Correction | Vérification |
|---|---|---|---|---|
| N-01 | Jeton de réinitialisation renvoyé dans la réponse HTTP | Critique | Lien envoyé par e-mail, jeton haché en base | 4 tests unitaires ; `curl` : réponse sans jeton |
| N-02 | Code à usage unique sans limite d'essais | Élevée | Limites par adresse et par compte, verrouillage | Test d'intégration : 429, verrouillage |
| N-03 | Sauvegarde sur le même serveur, non chiffrée, sans alerte ni procédure | Élevée | Chiffrement, copie S3 versionnée, alertes, procédure, test de restauration | 7 jours de sauvegardes vérifiées ; alerte sur échec simulé ; procès-verbal de restauration signé |
| N-04 | Consultations non journalisées | Élevée | Journal d'accès sur toutes les lectures, conservation 12 mois, écran d'administration | Tests d'intégration READ / DOWNLOAD |
| N-05 | Tout utilisateur connecté pouvait lire tout fichier par son chemin | Élevée | Fichiers rattachés à leur dossier, contrôle de droit avant envoi | Test : 403 hors site ou sans permission |
| N-06 | Framework serveur hors support ; aucune analyse des dépendances | Élevée | Montée de version, Dependency-Check et `npm audit` bloquants, Dependabot | Build vert sans vulnérabilité ≥ 7 |
| N-07 | Interface : injection via signature SVG, CSP non bloquante, en-têtes absents | Élevée | DOMPurify, nettoyage côté API, CSP bloquante, en-têtes | Test front ; `curl -I` ; Observatoire |
| N-08 | Compte rendu validé : ancien texte non conservé, contournement par la route de création | Moyenne | Table de versions, motif obligatoire, route de création verrouillée, journaux en ajout seul | Tests d'intégration |
| N-09 | Aucune supervision ; journal applicatif 30 jours dans le conteneur | Moyenne | Sonde externe, alertes, métriques 90 jours ; journal persistant 12 mois | Arrêt simulé → alerte ; journal présent après redémarrage |
| N-10 | Pas de verrouillage de compte ; adresse IP falsifiable | Moyenne | Verrouillage 15 min ; adresse lue uniquement depuis nginx | Tests |
| N-11 | 13 routes du tableau de bord sans permission | Moyenne | Permissions `VIEW_DASHBOARD` et `VIEW_DASHBOARD_FINANCE` | Test : 403 |
| N-12 | Auteur vide sur deux actions du journal des comptes rendus ; suppression non journalisée | Moyenne | Corrigé | Tests |
| N-13 | API déployée sans compilation ni tests ; interface sans tests | Moyenne | Tests exécutés en CI, déploiement bloqué en cas d'échec, retour arrière automatique ; premiers tests de l'interface | Commit volontairement cassé non déployé |
| N-14 | Chiffrement et S3 à confirmer en production ; secret TOTP en clair | Moyenne | Activés et vérifiés ; secret chiffré | Colonne vérifiée |
| N-15 | Script de migration et comptages non versionnés | Moyenne | Versionnés dans le dépôt | — |
| N-16 | Fichiers de données dans le dépôt ; politique de mot de passe minimale | Faible | Retirés de l'historique ; politique renforcée | `git ls-files storage` vide ; tests |

### 6.3 Test de restauration des sauvegardes

**Dispositif de sauvegarde.** Export complet de la base chaque jour à 18 h 30, chiffré, copié vers un bucket S3 distinct et versionné ; copie quotidienne des fichiers ; alerte en cas d'échec et contrôle indépendant chaque soir. Perte maximale théorique : 24 heures (RPO), conforme au niveau attendu du TdR.

**Déroulement du test.** Réalisé en présence de l'interlocuteur du laboratoire, à partir de la sauvegarde de la veille, sur un serveur vierge : récupération et déchiffrement de la sauvegarde, restauration de la base et des fichiers, démarrage de l'application, puis vérifications : comptages de patients, de demandes, de comptes rendus et de factures identiques à la production, ouverture d'un compte rendu et de son PDF, connexion d'un utilisateur. Délai de reprise total mesuré inférieur aux 4 heures attendues par le TdR. Le procès-verbal signé est remis avec ce rapport.

### 6.4 Vérification de la journalisation et de la détection

Vérifié en production :

- ouverture d'un dossier patient par un utilisateur de test → ligne dans le journal d'accès avec l'utilisateur, l'heure et l'adresse ;
- 25 connexions ratées en 10 minutes depuis un script → alerte reçue en moins de 10 minutes ;
- arrêt du conteneur de l'API pendant 3 minutes → alerte d'indisponibilité puis alerte de retour ;
- export de sauvegarde volontairement mis en échec → alerte reçue ;
- journal applicatif de la veille présent après redémarrage du conteneur.

### 6.5 Couverture par les tests automatisés

**API (labsys_api)** : 173 classes de test, 1 250 cas de test, dont 45 classes d'intégration exécutées sur une base PostgreSQL réelle (Testcontainers). Répartition par domaine :

| Domaine | Classes | Points couverts |
|---|---|---|
| Authentification et sessions | 4 + 1 | Connexion, cookies HttpOnly, déconnexion et révocation du jeton, rotation, code à usage unique par e-mail et par application, durées d'inactivité (30 min / 60 min médecin), réinitialisation du mot de passe par lien, limitation des essais et verrouillage |
| Rôles et permissions | 2 | Attribution des rôles, refus 403 sans permission, permissions du catalogue et des résultats de biologie |
| Fichiers et chiffrement | 6 | Chiffrement AES-256-GCM, activation, altération refusée, traversée de chemin, contrôle d'accès par dossier sur `/files` |
| Secrets | 2 | Chiffrement des secrets en base, chiffrement de la charge utile FluidPay |
| Appels par internet | 2 | Identifiants temporaires du relais, exemption des appels hors plage |
| Application mobile | 1 | Code d'enrôlement non réutilisable |
| Comptes rendus | 14 | Avancement, validation, rendu PDF, versions et trace après signature |
| Facturation et caisse | 25 | Factures, paiements, Mobile Money, clôtures, remboursements |
| Demandes d'examen | 18 | Création, affectations, macroscopie, étiquettes |
| Biologie, RH, stock, autres | 95 | Règles métier des autres modules |
| Journal d'accès | 1 | Lecture d'un dossier, d'un compte rendu et d'un fichier tracée avec le bon utilisateur ; purge à 12 mois |

**Interface web (labsys_front)** : tests des gardes de routes (`proxy.ts`), de la politique CSP (nonce, `strict-dynamic`, absence d'`unsafe-inline`), de l'intercepteur de rafraîchissement de session, de la fusion des permissions et du nettoyage des signatures SVG.

**Exécution** : les deux suites s'exécutent à chaque publication sur la branche principale, avec l'analyse des dépendances ; un échec bloque le déploiement.

### 6.6 Incidence des mesures de sécurité sur les performances

Les mesures de sécurité (double authentification, chiffrement des fichiers, vérification des permissions et journal d'accès à chaque requête, écrit de façon asynchrone) n'ont pas d'effet perceptible à l'usage. Les seuls changements visibles pour le personnel sont la saisie d'un code à usage unique à chaque connexion, la déconnexion après 15 minutes d'inactivité et la règle de mot de passe à 12 caractères.

---

## 7. Coûts récurrents d'exploitation

### 7.1 Évolution de la structure des coûts

| | Ancienne plateforme | Nouvelle plateforme |
|---|---|---|
| Hébergement | Serveur cPanel, ressources partagées, aucune isolation | Serveur dédié, conteneurs isolés, base PostgreSQL dédiée |
| Fichiers | Disque du serveur, exposé publiquement | Stockage S3 chiffré, redondant, hors du serveur |
| Sauvegardes | Sur le même serveur, en clair, publiques | Chiffrées, copiées sur un stockage distinct et versionné |
| Appels par internet | Inexistants | Relais d'appels hébergé sur le serveur dédié |
| Supervision | Aucune | Sonde externe, alertes, métriques |
| Maintenance | Aucune : composants jamais mis à jour depuis 2022 | Maintenance corrective gérée, mises à jour de sécurité régulières |
| Notifications | Service d'appels existant | FluidPay (appels vocaux et SMS) |
| **Coût annuel d'hébergement** | **300 000 FCFA** | **523 750 FCFA** |

L'ancien hébergement était bon marché parce qu'il n'offrait ni isolation, ni sauvegarde fiable, ni maintenance. Les nouveaux coûts correspondent aux garanties demandées par le TdR : disponibilité, confidentialité, reprise après incident et maintien à jour.

### 7.2 Coûts d'hébergement

Le forfait annuel d'hébergement et d'exploitation de la nouvelle plateforme est de **523 750 FCFA par an** (43 646 FCFA par mois), contre 300 000 FCFA par an pour l'ancien hébergement. Il couvre l'infrastructure, sa supervision et son administration. La maintenance de l'application (section 7.3) et les modules optionnels (section 7.4) sont distincts.

| Poste | Description | Coût annuel (FCFA) | Référence tarifaire publique |
|---|---|---|---|
| Serveur dédié à la plateforme | Instance cloud à ressources réservées : 8 vCPU, 16 Go de mémoire, 240 Go NVMe, 20 To de trafic mensuel, hébergée dans l'Union européenne. Porte l'application, l'interface web, la base PostgreSQL et le relais des appels par internet (coturn) | 231 480 | OVHcloud VPS : https://www.ovhcloud.com/fr/vps/ — Hetzner Cloud : https://www.hetzner.com/cloud/ |
| Stockage S3 | Fichiers médicaux chiffrés et sauvegardes versionnées, 250 Go, région Paris (eu-west-3), requêtes et transfert sortant compris | 43 920 | AWS S3 : https://aws.amazon.com/fr/s3/pricing/ — simulateur : https://calculator.aws/ |
| Supervision | Sonde externe chaque minute, alertes e-mail et SMS, métriques conservées 90 jours, rapport mensuel de disponibilité | Inclus | Uptime Kuma (logiciel libre) : https://github.com/louislam/uptime-kuma — Better Stack : https://betterstack.com/uptime/pricing |
| Exploitation | Administration système : mises à jour du système et des images de conteneurs, renouvellement des certificats TLS, traitement des alertes, vérification quotidienne des sauvegardes, rotation des journaux, rapport mensuel | 248 350 | — |
| **Total** | | **523 750** | |

Les montants des postes serveur et stockage correspondent aux tarifs publics des fournisseurs constatés à la date de rédaction, hors taxes, convertis au taux fixe de 1 € = 655,957 FCFA et à 1 $ ≈ 600 FCFA. Le laboratoire peut les vérifier à tout moment sur les pages indiquées. Les tarifs des fournisseurs pouvant évoluer, le forfait est révisable annuellement sur présentation des factures.

Un serveur physique dédié (OVHcloud Rise : https://www.ovhcloud.com/fr/bare-metal/rise/ — Hetzner AX : https://www.hetzner.com/dedicated-rootserver/) porterait le seul poste serveur à environ 309 400 FCFA par an. Il n'est pas nécessaire pour l'effectif actuel (10 à 20 utilisateurs) et n'est pas retenu.

### 7.3 Contrat de maintenance corrective

**Montant : 1 948 500 FCFA par an**, soit **162 375 FCFA par mois**, facturé par trimestre, révisable à chaque reconduction annuelle.

Le contrat est un forfait annuel à périmètre et délais définis, forme usuelle pour une application métier critique. Il couvre la maintenance de la plateforme **sans décompte de temps** : c'est une obligation de résultat, quel que soit l'effort nécessaire. À titre de comparaison, le marché européen situe la maintenance d'une application métier équivalente entre 1 500 et 5 000 € par mois, soit 985 000 à 3 280 000 FCFA par mois.

**Inclus dans le forfait**

| Domaine | Prestations |
|---|---|
| Maintenance corrective | Analyse et correction de toute anomalie de la plateforme, sur l'interface web, l'API et le socle de l'application mobile |
| Maintenance de sécurité | Application mensuelle des mises à jour de sécurité (framework, dépendances, PostgreSQL, images de conteneurs, système) ; traitement des alertes de vulnérabilités remontées par l'analyse automatique ; maintien du critère « composants supportés » du TdR, y compris les montées de version majeures du framework |
| Maintenance adaptative | Adaptation aux évolutions des services tiers : FluidPay (SMS, appels vocaux), FluidInvoice et e-MCF (DGI), Firebase, navigateurs, systèmes mobiles |
| Sauvegardes et reprise | Vérification quotidienne automatisée et contrôle mensuel manuel des sauvegardes ; test de restauration semestriel avec procès-verbal ; mise à jour de la procédure de restauration |
| Supervision | Traitement des alertes (indisponibilité, erreurs, tentatives répétées, espace disque) ; rapport mensuel de disponibilité en heures d'activité |
| Assistance | Réponse aux questions des utilisateurs par e-mail et téléphone, en heures d'activité ; aide à la revue trimestrielle des comptes et des permissions |
| Comptes utilisateurs | Création, modification et désactivation de comptes à la demande de la direction ; réinitialisations assistées |
| Suivi | Rapport mensuel de disponibilité ; rapport trimestriel d'activité (anomalies traitées, mises à jour appliquées, disponibilité mesurée) ; revue semestrielle des journaux de sécurité ; réunion annuelle de bilan avec mise à jour du présent rapport |

**Hors forfait**

Toute demande qui ne relève pas de la maintenance est facturée **65 000 FCFA par jour**, avec un **minimum d'une demi-journée (32 500 FCFA) par intervention**, quelle que soit sa durée. Chaque demande fait l'objet d'une estimation en demi-journées, validée par le laboratoire avant réalisation. Sont concernés, par exemple : l'ajout d'un champ, d'un filtre ou d'un export, la modification d'un modèle de compte rendu ou de facture, un nouveau type d'examen ou de circuit de validation, une nouvelle fonctionnalité ou un nouveau module, une intégration avec un système tiers, la formation collective des utilisateurs, la reprise ou l'export de données vers un autre système, les interventions sur site hors Cotonou.

**Engagements de délais**, en heures d'activité du laboratoire (lundi–vendredi 7 h 30–18 h 30, samedi 8 h–13 h) :

| Niveau | Définition | Exemple | Prise en charge (GTI) | Rétablissement (GTR) |
|---|---|---|---|---|
| P1 – Critique | Plateforme inaccessible ou suspicion de fuite de données | Impossible de se connecter ; alerte de sécurité | 1 h | 4 h |
| P2 – Majeur | Fonction essentielle bloquée, sans contournement | Validation ou impression des comptes rendus impossible ; facturation bloquée | 2 h | 8 h |
| P3 – Mineur | Fonction dégradée, contournement possible | Erreur sur un écran secondaire ; notification non envoyée | 4 h | 3 jours ouvrés |
| P4 – Faible | Gêne sans incidence sur l'activité | Affichage, libellé, confort | 1 jour ouvré | Prochaine mise à jour planifiée |

Le GTR de 4 heures pour un incident critique correspond au délai maximal de reprise fixé par le TdR (F5). Les délais sont suspendus hors heures d'activité ; un incident P1 signalé un samedi après 13 h est pris en charge le lundi à 7 h 30, sauf astreinte convenue séparément.

**Signalement** : par e-mail à l'adresse de support et, pour les incidents P1, par téléphone. Chaque signalement reçoit un numéro de suivi.

### 7.4 Modules optionnels

| Module | Tarif | Base de calcul |
|---|---|---|
| Facturation normalisée (FluidInvoice, e-MCF / DGI) | 20 000 FCFA / mois | Forfait |
| Envoi de SMS (avis au patient, lien de facture) | 25 FCFA / SMS | Volume mensuel |
| Notification par appel vocal en langue locale | 3 FCFA / seconde | Nombre d'appels × durée moyenne |

Exemple de calcul des appels vocaux, à titre d'illustration : 300 appels par mois d'une durée moyenne de 30 secondes = 9 000 secondes × 3 FCFA = **27 000 FCFA / mois**.

---

## 8. Risques résiduels et recommandations

### 8.1 Risques résiduels techniques

| Risque | Mesure compensatoire |
|---|---|
| Brève interruption (moins d'une minute) à chaque déploiement | Déploiements hors heures ouvrées ; retour arrière automatique |
| Un compte rendu livré reste modifiable par un pathologiste signataire | Version précédente conservée, motif obligatoire, alerte aux administrateurs, journaux inaltérables. Alternative possible : interdiction stricte et flux d'addendum, sur demande du laboratoire |

### 8.2 Recommandations organisationnelles à l'attention du laboratoire

| Recommandation | Fonction du TdR | Référence ISO 27002 |
|---|---|---|
| Formaliser la procédure d'attribution et de retrait des accès, notamment au départ d'un employé, et la revue trimestrielle des comptes (le journal d'accès et l'écran des utilisateurs permettent cette revue) | F1 | 5.18 |
| Désigner un responsable du signalement des incidents à la direction (délai de 24 h) et rédiger la procédure ; les alertes techniques lui sont adressées | F6 | 5.24, 5.26 |
| Effectuer les formalités auprès de l'APDP : registre des traitements, déclaration, droits des personnes | Conformité | 5.31, 5.34 |
| Vérifier le pays d'hébergement des serveurs (dédié, S3, appels) au regard des règles de transfert du Livre V, et le documenter dans le registre | Conformité | 5.14 |
| Sensibiliser le personnel : hameçonnage, code à usage unique, verrouillage des postes | F1 | 6.3 |
| Définir la durée de conservation des dossiers et des fichiers | F2, coûts | 5.33 |

---

## 9. Maintien en condition de sécurité

Le maintien en condition de sécurité est assuré par KAWA SERVICES dans le cadre du contrat de maintenance (section 7.3) et du forfait d'hébergement (section 7.2). Le laboratoire n'a pas de tâche technique à sa charge ; sa contribution se limite aux points organisationnels de la section 8.2 et aux rendez-vous ci-dessous.

| Fréquence | Action | Assurée par | Cadre |
|---|---|---|---|
| Continue | Surveillance de la plateforme, traitement des alertes, analyse automatique des composants à chaque mise à jour | KAWA SERVICES | Hébergement (7.2) et maintenance (7.3) |
| Mensuelle | Application des mises à jour de sécurité ; contrôle des sauvegardes ; rapport de disponibilité adressé à la direction | KAWA SERVICES | Maintenance (7.3) |
| Trimestrielle | Rapport d'activité (anomalies traitées, mises à jour appliquées, disponibilité) ; revue des comptes et des permissions à partir du journal d'accès, avec le laboratoire | KAWA SERVICES, avec l'interlocuteur du laboratoire | Maintenance (7.3) |
| Semestrielle | Test de restauration avec procès-verbal ; revue des journaux de sécurité | KAWA SERVICES, en présence du laboratoire pour le test | Maintenance (7.3) |
| Annuelle | Réunion de bilan avec la direction ; mise à jour de ce rapport (état des composants, registre des faiblesses, coûts) ; reconduction du contrat | KAWA SERVICES et direction du CAAP | Maintenance (7.3) |


---
