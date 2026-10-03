> **CONTRÔLE AVANT REMISE – à retirer dans la version livrée**
>
> Ce rapport décrit la plateforme **après** exécution de `SPEC-corrections-securite.md`. Il ne doit être remis à la direction qu'une fois les points suivants vrais :
>
> - [ ] Lots 1 à 11 de la spécification fusionnés et déployés en production
> - [ ] Test de restauration réalisé avec le laboratoire, procès-verbal signé
> - [ ] Anciens secrets changés, ancienne plateforme arrêtée
>

---

# Rapport de prestation (L1)
## Renforcement de la sécurité de la plateforme de gestion du laboratoire CAAP

---

## 0. Contrôle documentaire

| | |
|---|---|
| Document | Rapport de prestation – livrable L1 du TdR « Renforcement de la sécurité de la plateforme de gestion du laboratoire et de ses données » |
| Destinataire | Direction du CAAP |
| Classification | **Confidentiel** |
| Prestataire | KAWA SERVICES |
| Interlocuteur du laboratoire | ELGA DOSSOU-YOVO |

---

## Glossaire

| Terme | Définition |
|---|---|
| APDP | Autorité de Protection des Données Personnelles du Bénin |
| Application mobile | Application installée sur un téléphone, utilisée par les médecins et le personnel |
| Chiffrement | Transformation d'une donnée pour qu'elle ne soit lisible qu'avec une clé |
| Cloisonnement | Règle qui empêche un utilisateur de voir les données d'un autre site ou d'un autre dossier que les siens |
| Code à usage unique | Code à six chiffres reçu par e-mail ou généré par une application, demandé à chaque connexion en plus du mot de passe |
| Composant | Brique logicielle fournie par un tiers (fondation logicielle, bibliothèque, base de données) sur laquelle la plateforme repose |
| CVSS | Échelle internationale de 0 à 10 qui note la gravité d'une faiblesse : Critique ≥ 9, Élevée 7 à 8,9, Moyenne 4 à 6,9, Faible < 4 |
| Déploiement | Mise en service d'une nouvelle version de la plateforme |
| DGI | Direction Générale des Impôts, destinataire des factures normalisées |
| Double authentification | Connexion en deux étapes : mot de passe, puis code à usage unique |
| Fin de support | Date à partir de laquelle l'éditeur d'un composant ne corrige plus ses failles de sécurité |
| FluidInvoice | Service de facturation normalisée, relié à la DGI |
| FluidPay | Service d'envoi de SMS et d'appels vocaux |
| Fondation logicielle (framework) | Socle sur lequel une application est construite |
| ISO 27001 / 27002 | Normes internationales de gestion de la sécurité de l'information et catalogue des mesures associées |
| ISO 22301 | Norme internationale de continuité d'activité (sauvegardes, reprise après incident) |
| Journal d'accès | Enregistrement de qui a consulté quel dossier, et quand |
| OWASP | Organisation internationale de référence pour la sécurité des applications ; publie le Top 10 des failles et le référentiel ASVS |
| Permission | Droit précis accordé à un rôle, par exemple « valider un compte rendu » |
| Procès-verbal de restauration | Document signé attestant qu'une sauvegarde a été remise en service avec succès, en présence du laboratoire |
| Rôle | Ensemble de permissions attribué à un utilisateur selon sa fonction (secrétaire, technicien, pathologiste, comptable…) |
| Sauvegarde hors site | Copie des sauvegardes sur un emplacement distinct du serveur, pour survivre à sa panne |
| Serveur dédié | Serveur réservé à la seule plateforme |
| Session | Période pendant laquelle un utilisateur connecté est reconnu ; elle se ferme après inactivité |
| Supervision | Surveillance automatique de la disponibilité et du bon fonctionnement, avec alertes |
| Suppression logique | Marquage d'une donnée comme supprimée sans l'effacer réellement, pour conserver l'historique |
| Test automatisé | Programme qui vérifie qu'une fonction se comporte comme prévu, exécuté à chaque modification |
| Traçabilité | Capacité à savoir qui a consulté, créé ou modifié une donnée, et quand |
| Versionnage | Conservation de chaque état successif d'un fichier ou d'un compte rendu |

---

## 1. Résumé exécutif

### 1.1 Objet de la prestation et réalisations

La plateforme en service depuis 2022, développée par KAWA SERVICES, reposait sur les composants de référence de l'époque. Faute de contrat de maintenance, elle n'a pas suivi leur cycle de vie : ses deux fondations logicielles ne reçoivent plus de correctifs de sécurité de leur éditeur, l'une depuis janvier 2023, l'autre depuis décembre 2025. L'audit, conduit selon les référentiels internationaux actuels et au regard des exigences nouvelles de l'APDP, a relevé vingt-et-une faiblesses, dont dix de niveau « élevé ». Quatre d'entre elles exposaient directement des données de santé.

Corriger cette plateforme n'était ni la voie la plus sûre ni la plus économique. Le laboratoire a donc été doté d'une **nouvelle plateforme**, construite sur des composants récents et maintenus, avec une interface entièrement refaite, une base de données moderne, un stockage de fichiers chiffré, des sauvegardes chiffrées copiées hors du serveur, un journal des accès, une supervision permanente, et de nouvelles fonctions (appels par internet, notifications vocales en langue locale, SMS, facturation normalisée intégrée). L'intégralité des données a été reprise. La plateforme sert également de socle à l'application mobile, objet d'une prestation distincte engagée en 2025 et finalisée en 2026.

La nouvelle plateforme a elle-même été auditée selon le même référentiel. Les seize points relevés ont été corrigés et vérifiés avant la remise de ce rapport.

### 1.2 Évaluation des critères de réussite (TdR §2.3)

| Résultat attendu | Critère du TdR | Verdict |
|---|---|---|
| L'état de sécurité est connu | État des lieux remis et présenté | **Atteint** – section 3 |
| Les faiblesses graves sont corrigées | Toutes les faiblesses « critiques » ou « élevées » traitées et vérifiées | **Atteint** – les 21 faiblesses de l'ancienne plateforme sont fermées ; les 16 points relevés sur la nouvelle sont corrigés et vérifiés (section 6) |
| La plateforme est à jour | Tous les composants bénéficient d'un support de sécurité | **Atteint** – et vérifié en continu : tout déploiement est bloqué si un composant présente une faille connue |
| Les données peuvent être restaurées | Test de restauration réussi en présence du laboratoire | **Atteint** – test réalisé en présence du laboratoire, reprise en moins de 4 heures, procès-verbal signé |

### 1.3 Conformité aux fonctions attendues F1 à F6

| N° | Fonction | Niveau attendu | Niveau atteint | Mesures |
|---|---|---|---|---|
| F1 | Confidentialité | Aucun accès non autorisé | Atteint | Connexion en deux étapes obligatoire, droits précis par rôle, cloisonnement par site et par dossier, fichiers chiffrés et accessibles uniquement après contrôle de droit, blocage des tentatives répétées |
| F2 | Intégrité | Modification d'un compte rendu validé impossible sans trace | Atteint | Toute modification après signature conserve la version précédente, exige un motif et alerte les administrateurs ; les journaux ne peuvent pas être altérés |
| F3 | Disponibilité | 99 % en heures ouvrées | Atteint, mesuré | Surveillance externe chaque minute, rapport mensuel de disponibilité, alerte en 2 minutes |
| F4 | Traçabilité | 12 mois d'historique | Atteint | Journal des consultations (qui a lu quel dossier, quand), journaux des actions, conservation 12 mois |
| F5 | Reprise | Perte max. 24 h, reprise en 4 h | Atteint | Sauvegarde quotidienne chiffrée, copiée hors du serveur, alerte en cas d'échec, procédure écrite, test réalisé |
| F6 | Détection | Signalement en 24 h | Atteint | Alertes immédiates sur indisponibilité, tentatives répétées, erreurs, échec de sauvegarde et modification après signature, adressées aux administrateurs du laboratoire et au prestataire |

### 1.4 Éléments budgétaires

- Le TdR prévoyait une enveloppe de 1 150 000 pour renforcer la plateforme existante. L'audit a montré que cette plateforme devait être remplacée ; l'effort supplémentaire a été absorbé dans le cadre de la prestation (section 4.2).
- La nouvelle architecture entraîne des **coûts récurrents** plus élevés que l'ancien hébergement : le forfait d'hébergement et d'exploitation passe de 300 000 à 523 750 FCFA par an. S'y ajoutent le contrat de maintenance de l'application (1 948 500 FCFA par an) et les modules optionnels. Le détail figure en section 7. Sans maintenance régulière, la nouvelle plateforme se retrouvera dans la situation de l'ancienne au bout de quelques semaines ou mois.

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
- **L'hébergement** : architecture de déploiement, configuration des serveurs, chaîne de mise en production, supervision.
- **Les utilisateurs** : règles de connexion, droits d'accès par rôle, journal des accès, outils de revue des comptes.
- **L'organisation** : recommandations de procédures internes (section 8.2) et dispositif de maintien en condition de sécurité (section 9).

### 2.3 Référentiels et méthodologie

L'audit s'appuie sur les référentiels internationaux de référence : les normes ISO 27001 et 27002 pour les mesures de sécurité, l'OWASP (ASVS et Top 10) pour la sécurité des applications, l'échelle CVSS pour coter la gravité de chaque faiblesse, la norme ISO 22301 pour la sauvegarde et la reprise, et le Code du numérique du Bénin (Livre V) pour la protection des données.

L'audit a été conduit par revue du code source et de la configuration des deux plateformes, complétée par des tests automatisés et des tests en conditions réelles (restauration, alertes). Aucune donnée réelle n'est sortie de l'hébergement du laboratoire.

---

## 3. État des lieux initial de la plateforme existante

### 3.1 Situation de départ

L'ancienne plateforme, développée par KAWA SERVICES et mise en service en 2022, a été construite sur les composants de référence de l'époque, dans le cadre budgétaire et d'hébergement alors retenu, pour un outil interne à effectif réduit. Elle n'a pas fait l'objet d'un contrat de maintenance par la suite.

L'état des lieux mesure l'écart entre cette plateforme et les exigences de 2026 : référentiels de sécurité actualisés, attention accrue de l'APDP aux données de santé, et besoins du laboratoire qui ont évolué (accès distants, application mobile, traçabilité). La majorité des constats relève de trois causes : la fin de support des composants, l'absence de mises à jour depuis la mise en service, et des exigences qui ne faisaient pas partie du périmètre de 2022.

### 3.2 Composants hors support

Quatre ans après la mise en service, sans mise à jour, aucune des couches de l'application ne bénéficiait plus d'un support de sécurité de son éditeur :

| Couche | Constat |
|---|---|
| Fondation logicielle (framework) | Plus de correctifs de sécurité depuis le 24 janvier 2023 |
| Langage de programmation | Plus de correctifs de sécurité depuis le 31 décembre 2025 |
| Base de données | Fin de support en juin 2025 |
| Bibliothèques d'affichage et de génération de documents | Plusieurs versions comportant des failles publiques, corrigées par leurs éditeurs dans des versions postérieures à 2022 |
| Scripts chargés depuis des serveurs tiers | Une soixantaine, sans contrôle d'intégrité : une compromission chez le tiers aurait touché la plateforme |

C'est le cycle de vie normal de composants non maintenus : le critère « tous les composants supportés » du TdR ne pouvait plus être rempli sans intervention.

### 3.3 Faiblesses relevées

Vingt-et-une faiblesses ont été relevées et cotées sur l'échelle CVSS. Elles se regroupent en cinq familles.

**Exposition de données de santé (3 faiblesses, dont 2 élevées).** Les sauvegardes complètes de la base et les comptes rendus de patients étaient enregistrés dans un espace du serveur accessible depuis internet, sous des noms faciles à deviner. Une personne extérieure pouvait, en principe, télécharger l'intégralité des données sans mot de passe. Les images de signature des pathologistes étaient dans la même situation.

**Comptes et connexion (5 faiblesses, dont 4 élevées).** La connexion en deux étapes pouvait être contournée ; les nouveaux comptes recevaient un mot de passe initial identique, sans obligation de le changer ; les tentatives de connexion n'étaient pas limitées ; les paramètres de l'application, y compris des mots de passe de services tiers, étaient lisibles et modifiables par tout utilisateur connecté ; des secrets de configuration figuraient dans l'historique du code.

**Contrôle d'accès (5 faiblesses, dont 3 élevées).** Un utilisateur connecté pouvait consulter le dossier d'un autre patient ou une facture en changeant simplement un numéro dans l'adresse de la page. L'accès de l'application mobile n'avait ni expiration ni contrôle de rôle. Certaines actions de suppression pouvaient être déclenchées par un simple lien. Un fichier déposé comme signature aurait pu être exécuté par le serveur.

**Intégrité et traçabilité (4 faiblesses).** Un compte rendu validé pouvait être réécrit ou « signé » au nom de n'importe quel pathologiste ; la journalisation de ces modifications n'était pas effective et aucune version antérieure n'était conservée. Personne ne pouvait savoir qui avait consulté quel dossier. Les patients, factures et utilisateurs supprimés l'étaient définitivement.

**Configuration (4 faiblesses, de gravité moyenne à faible).** Mode de débogage actif, en-têtes de sécurité absents, restriction horaire de connexion inopérante, protections par défaut non activées.

| Gravité | Nombre |
|---|---|
| Critique | 0 |
| Élevée | 10 |
| Moyenne | 10 |
| Faible | 1 |
| **Total** | **21** |

Aucune faiblesse n'atteint le niveau « critique » sur l'échelle CVSS, parce que chacune, prise isolément, suppose une condition. En revanche, quatre d'entre elles sont **critiques en impact pour le laboratoire** : elles portent sur des données de santé et sur l'intégrité de diagnostics signés. Enchaînées, plusieurs d'entre elles menaient à une prise de contrôle complète du serveur.

### 3.4 Hébergement, données et sauvegardes

La plateforme était hébergée sur un serveur partagé de type cPanel, avec déploiement manuel et base de données sur le même serveur. Tous les fichiers médicaux étaient sur le disque de ce serveur, dans un espace accessible depuis internet. Une seule sauvegarde quotidienne existait, sur le même serveur, non chiffrée, sans copie externe, sans sauvegarde des fichiers et sans test de restauration ; un échec restait silencieux. Le serveur de messagerie référencé suggère un hébergeur dont l'infrastructure est au Canada ; si c'est le cas, des données de santé étaient hébergées hors du Bénin, ce qui relève des règles de transfert du Livre V du Code du numérique.

### 3.5 Conclusion de l'état des lieux

Une correction incrémentale n'était ni la voie la plus sûre ni la plus économique :

1. **Le socle était hors support.** Revenir sur un socle maintenu supposait quatre montées de version majeures et le remplacement de la plupart des composants : l'essentiel du code était à reprendre.
2. **La couverture de tests était limitée.** La plateforme de 2022 avait été livrée sans budget de tests automatisés ni contrat de maintenance ; une remise à niveau de cette ampleur ne pouvait pas être validée sans risque de régression sur des données de santé.
3. **Le contrôle d'accès, la traçabilité et le stockage des fichiers étaient à concevoir.** Conçus pour un outil interne à effectif réduit, ils ne s'adaptent pas aux exigences actuelles (accès distants, médecins externes, application mobile, journal des accès, immutabilité des comptes rendus) sans restructuration.
4. **Les secrets de configuration présents dans l'historique du dépôt** devaient être changés quel que soit le scénario.

Le coût et le risque d'une remise à niveau étaient comparables à ceux d'une réécriture, sans en apporter les garanties. La refonte a permis de repartir sur un socle maintenu, avec les exigences de 2026 intégrées dès la conception, et de les préserver dans le temps par le contrat de maintenance (section 7.3).

---

## 4. Analyse des options et décision de refonte

### 4.1 Options évaluées

| Option | Description | Évaluation |
|---|---|---|
| A. Corriger l'existant | Traiter les 21 faiblesses une à une sur le socle actuel | Le socle reste hors support : le critère « composants supportés » du TdR ne peut pas être atteint. Chaque correction se fait sans tests, sur du code à risque. |
| B. Migrer le socle | Monter le socle vers ses versions récentes, puis corriger | Quatre montées majeures, remplacement de la majorité des composants. Le contrôle d'accès, la traçabilité et le stockage restent à refaire. Effort proche de C, sans ses bénéfices. |
| C. Refondre | Nouvelle plateforme sur des composants récents, reprise intégrale des données | Permet un contrôle d'accès centralisé, une traçabilité dès la conception, un stockage privé et chiffré, des tests automatisés. Seule option compatible avec les cinq objectifs du TdR. **Retenue.** |

### 4.2 Incidence budgétaire

Le TdR prévoyait une enveloppe de 1 150 000 pour le renforcement de la plateforme existante. Le choix de la refonte, rendu nécessaire par l'état de la plateforme (section 3.5), a représenté un effort de développement supérieur à cette enveloppe. Cet écart a été absorbé dans le cadre de la prestation, sans modification du montant convenu.

Le laboratoire bénéficie en outre d'améliorations qui n'étaient pas demandées par le TdR : interface entièrement refaite, appels par internet, notifications vocales en langue locale, SMS, facturation normalisée intégrée, gains de performance (section 5). L'application mobile relève d'une prestation distincte (2025–2026) ; la présente plateforme en fournit le socle technique.

---

## 5. Description de la plateforme mise à niveau

### 5.1 Ce qui change

| | Ancienne plateforme | Nouvelle plateforme |
|---|---|---|
| Fondations logicielles | Hors support depuis 2023 et 2025 | Composants récents, tous supportés, vérifiés automatiquement à chaque déploiement |
| Hébergement | Serveur partagé, déploiement manuel | Serveur dédié, déploiement automatisé avec tests préalables et retour arrière en cas de défaillance |
| Base de données | Sur le même serveur, sans chiffrement | Base de données moderne, sauvegardée chaque jour de façon chiffrée |
| Fichiers médicaux | Sur le disque du serveur, accessibles depuis internet | Chiffrés, stockés hors du serveur, accessibles uniquement après contrôle de droit |
| Sauvegardes | Une par jour, sur le même serveur, en clair | Une par jour, chiffrée, copiée sur un stockage distinct et versionné, avec alerte en cas d'échec |
| Connexion | Mot de passe seul en pratique | Mot de passe et code à usage unique à chaque connexion, session fermée après inactivité |
| Droits d'accès | Vérifiés écran par écran | Plus de 110 permissions distinctes, vérifiées sur chaque action, cloisonnement par site et par dossier |
| Traçabilité | Partielle | Journal des consultations et des actions, conservé 12 mois, inaltérable |
| Supervision | Aucune | Surveillance externe permanente, alertes immédiates, rapport mensuel de disponibilité |
| Interface | Pages web classiques, dépendantes d'une soixantaine de scripts tiers | Interface entièrement refaite, sans aucune dépendance externe, adaptée au téléphone |

### 5.2 Reprise des données

L'intégralité des données de l'ancienne plateforme a été reprise : patients, demandes d'examen, comptes rendus, factures, opérations de caisse, personnel, documents. La base d'origine (85 tables) a été convertie vers le nouveau modèle (79 tables), avec correction de défauts d'encodage hérités et vérification de complétude par comparaison des totaux métier (nombre de comptes rendus par statut, totaux de caisse). Les fichiers ont été copiés puis migrés vers le stockage chiffré. Deux écarts hérités de l'ancien modèle (sessions de caisse jamais fermées, opérations de caisse sans facture associée) sont documentés et suivis.

### 5.3 Refonte de l'interface utilisateur

L'interface a été entièrement réécrite : 86 écrans, un menu unique organisé par métier et filtré selon les droits de chacun, une mise en page adaptée au téléphone, des confirmations avant toute suppression, et une déconnexion automatique après 15 minutes d'inactivité avec préavis. Les règles nouvelles pour le personnel se limitent à trois gestes : saisir le code à usage unique à la connexion, choisir un mot de passe d'au moins 12 caractères, et se reconnecter après une période d'inactivité.

### 5.4 Mesures de sécurité mises en œuvre

**F1 – Confidentialité.** Connexion en deux étapes obligatoire ; mots de passe stockés sous forme d'empreinte, avec une règle de 12 caractères minimum et le refus des mots de passe courants ; blocage temporaire après des tentatives répétées ; réinitialisation du mot de passe par lien envoyé par e-mail, valable une heure ; session fermée après 30 minutes d'inactivité ; droits précis par rôle, vérifiés par le serveur sur chaque action ; cloisonnement par site et par dossier, un médecin ne voyant que ses propres demandes ; fichiers chiffrés et servis uniquement après contrôle de droit ; application mobile avec code PIN, appareils révocables individuellement.

**F2 – Intégrité.** Avant toute modification d'un compte rendu validé, la version complète précédente est conservée et reste consultable ; la modification exige un motif, est tracée et signalée par e-mail aux administrateurs ; les journaux sont en ajout seul, l'application elle-même ne peut ni les modifier ni les effacer ; les données supprimées sont conservées avec un marqueur.

**F3 – Disponibilité.** Surveillance externe chaque minute ; redémarrage automatique en cas d'arrêt ; déploiement contrôlé avec retour automatique à la version précédente si la nouvelle ne démarre pas ; alertes sur erreurs répétées et espace disque.

**F4 – Traçabilité.** Journal des consultations : qui a lu, téléchargé ou imprimé quel dossier, compte rendu, fichier ou facture, et quand ; journaux des actions sur les comptes rendus, les factures, les remboursements, l'application mobile ; conservation de 12 mois ; écran de consultation réservé aux administrateurs, avec export.

**F5 – Reprise après incident.** Sauvegarde complète chaque soir, chiffrée, copiée vers un stockage distinct et versionné ; sauvegarde quotidienne des fichiers ; alerte en cas d'échec et contrôle indépendant chaque soir ; procédure de restauration écrite ; test de restauration réalisé en présence du laboratoire.

**F6 – Détection.** Alertes immédiates en cas d'indisponibilité, de tentatives de connexion répétées, d'erreurs serveur, d'échec de sauvegarde, de modification d'un compte rendu après signature ; indicateurs conservés 90 jours.

### 5.5 Performances

La nouvelle plateforme est sensiblement plus rapide à l'usage : programme compilé au lieu d'un script interprété à chaque requête, base de données indexée pour les recherches, listes chargées page par page, plus aucune ressource chargée depuis des serveurs tiers.

| Écran | Ancienne plateforme | Nouvelle plateforme |
|---|---|---|
| Liste des demandes | | |
| Ouverture d'un compte rendu | | |
| Recherche patient | | |

Mesures : même poste, même réseau, médiane de 5 essais.

### 5.6 Périmètre fonctionnel

**Fonctionnalités conservées** (toutes les fonctions de l'ancienne plateforme, §4.3 du TdR) : patients, médecins et établissements, catalogue d'examens, demandes d'examen et affectations, comptes rendus avec modèles et signatures, consultations et rendez-vous, facturation, paiements, Mobile Money, caisse, dépenses, banque, remboursements, facturation normalisée e-MECeF, contrats, personnel, stock, gestion documentaire, support, messagerie, tableau de bord, rôles et paramètres.

**Fonctionnalités nouvelles** :

| Fonction | Description |
|---|---|
| Appels par internet | Appels audio entre l'application mobile et le laboratoire, sans passer par l'opérateur téléphonique |
| Notifications vocales en langue locale | Appel automatique du patient avec un message en fon quand son compte rendu est disponible |
| SMS | Avis au patient avec lien sécurisé de téléchargement de sa facture |
| Facturation normalisée intégrée | Transmission des factures à la DGI depuis la plateforme, module optionnel |
| Discussions par dossier | Échanges internes rattachés à une demande d'examen |
| Dépôts bancaires et bons de caisse | Suivi de la caisse jusqu'au dépôt en banque |
| Historique des affectations | Qui a pris en charge quelle demande, et quand |
| Versions des comptes rendus | Chaque état d'un compte rendu signé est conservé et comparable |
| Journal d'accès | Qui a consulté quel dossier, consultable par les administrateurs |
| Socle de l'application mobile (prestation distincte, 2025–2026) | Enrôlement par code, PIN, appareils révocables, travail hors ligne |

---

## 6. Vérification de l'efficacité des mesures

### 6.1 Traitement des faiblesses initiales

Pour chacune des 21 faiblesses de la section 3, l'état de la nouvelle plateforme a été vérifié par revue du code, puis par test automatisé ou test en production.

| Famille | Faiblesses | Statut |
|---|---|---|
| Exposition de données de santé | 3 | Fermées : fichiers et sauvegardes chiffrés, hors du serveur web, accessibles uniquement après contrôle de droit |
| Comptes et connexion | 5 | Fermées : connexion en deux étapes obligatoire, règles de mot de passe, blocage des tentatives, paramètres protégés, secrets hors du code et changés |
| Contrôle d'accès | 5 | Fermées : droits vérifiés sur chaque action, cloisonnement par site et par dossier, application mobile sécurisée |
| Intégrité et traçabilité | 4 | Fermées : versions des comptes rendus, modification tracée et signalée, journal des consultations, suppression logique |
| Configuration | 4 | Fermées : erreurs génériques, en-têtes de sécurité complets, analyse automatique des dépendances |
| **Total** | **21** | **21 fermées** |

### 6.2 Audit de la plateforme mise à niveau

La nouvelle plateforme a été auditée avec le même référentiel. Seize points ont été relevés, corrigés et vérifiés par des tests automatisés avant la remise de ce rapport. Les plus importants portaient sur la réinitialisation du mot de passe, la limitation des tentatives sur le code de connexion, l'emplacement des sauvegardes, le journal des consultations, le contrôle d'accès aux fichiers et la mise à niveau du socle.

### 6.3 Test de restauration des sauvegardes

**Dispositif de sauvegarde.** Export complet de la base chaque jour, chiffré, copié vers un stockage distinct et versionné ; copie quotidienne des fichiers ; alerte en cas d'échec et contrôle indépendant chaque soir. Perte maximale théorique : 24 heures, conforme au niveau attendu du TdR.

**Déroulement du test.** Réalisé en présence de l'interlocuteur du laboratoire, à partir de la sauvegarde de la veille, sur un serveur vierge : récupération et déchiffrement de la sauvegarde, restauration de la base et des fichiers, démarrage de l'application, puis vérifications : nombres de patients, de demandes, de comptes rendus et de factures identiques à la production, ouverture d'un compte rendu et de son PDF, connexion d'un utilisateur. Délai de reprise total mesuré inférieur aux 4 heures attendues par le TdR. Le procès-verbal signé est remis avec ce rapport.

### 6.4 Vérification de la journalisation et de la détection

Vérifié en production :

- ouverture d'un dossier patient par un utilisateur de test → ligne dans le journal d'accès avec l'utilisateur, l'heure et l'adresse ;
- 25 connexions ratées en 10 minutes depuis un script → alerte reçue en moins de 10 minutes ;
- arrêt du serveur d'application pendant 3 minutes → alerte d'indisponibilité puis alerte de retour ;
- sauvegarde volontairement mise en échec → alerte reçue ;
- journal de la veille présent après redémarrage.

### 6.5 Tests automatisés

La nouvelle plateforme est accompagnée de 1 250 tests automatisés, dont 45 séries exécutées sur une base de données réelle. Ils couvrent notamment la connexion, la double authentification, les limites de tentatives, les permissions, le chiffrement des fichiers, le journal d'accès et les versions des comptes rendus. Ils s'exécutent à chaque mise à jour ; un échec bloque le déploiement.

### 6.6 Incidence des mesures de sécurité sur les performances

Les mesures de sécurité n'ont pas d'effet perceptible à l'usage. Les seuls changements visibles pour le personnel sont la saisie d'un code à usage unique à chaque connexion, la déconnexion après 15 minutes d'inactivité et la règle de mot de passe à 12 caractères.

---

## 7. Coûts récurrents d'exploitation

### 7.1 Évolution de la structure des coûts

| | Ancienne plateforme | Nouvelle plateforme |
|---|---|---|
| Hébergement | Serveur partagé, aucune isolation | Serveur dédié, base de données dédiée |
| Fichiers | Disque du serveur, exposé publiquement | Stockage chiffré, redondant, hors du serveur |
| Sauvegardes | Sur le même serveur, en clair, publiques | Chiffrées, copiées sur un stockage distinct et versionné |
| Appels par internet | Inexistants | Relais d'appels hébergé sur le serveur dédié |
| Supervision | Aucune | Surveillance externe, alertes, indicateurs |
| Maintenance | Aucune : composants jamais mis à jour depuis 2022 | Contrat de maintenance, mises à jour de sécurité régulières |
| Notifications | Service d'appels existant | FluidPay (appels vocaux et SMS) |
| **Coût annuel d'hébergement** | **300 000 FCFA** | **523 750 FCFA** |

L'ancien hébergement était bon marché parce qu'il n'offrait ni isolation, ni sauvegarde fiable, ni maintenance. Les nouveaux coûts correspondent aux garanties demandées par le TdR : disponibilité, confidentialité, reprise après incident et maintien à jour.

### 7.2 Coûts d'hébergement

Le forfait annuel d'hébergement et d'exploitation de la nouvelle plateforme est de **523 750 FCFA par an** (43 646 FCFA par mois), contre 300 000 FCFA par an pour l'ancien hébergement. Il couvre l'infrastructure, sa supervision et son administration. La maintenance de l'application (section 7.3) et les modules optionnels (section 7.4) sont distincts.

| Poste | Description | Coût annuel (FCFA) | Référence tarifaire publique |
|---|---|---|---|
| Serveur dédié à la plateforme | Serveur réservé à la plateforme, hébergé dans l'Union européenne. Porte l'application, l'interface web, la base de données et le relais des appels par internet | 231 480 | OVHcloud : https://www.ovhcloud.com/fr/vps/ — Hetzner : https://www.hetzner.com/cloud/ |
| Stockage des fichiers et des sauvegardes | 250 Go, chiffrés, région Paris | 43 920 | Amazon S3 : https://aws.amazon.com/fr/s3/pricing/ |
| Supervision | Surveillance externe chaque minute, alertes e-mail et SMS, rapport mensuel de disponibilité | Inclus | Uptime Kuma (logiciel libre) : https://github.com/louislam/uptime-kuma |
| Exploitation | Administration du serveur : mises à jour du système, certificats, traitement des alertes, vérification quotidienne des sauvegardes, rapport mensuel | 248 350 | n/a |
| **Total** | | **523 750** | |

Les montants des postes serveur et stockage correspondent aux tarifs publics des fournisseurs constatés à la date de rédaction, hors taxes, convertis au taux fixe de 1 € = 655,957 FCFA et à 1 $ ≈ 600 FCFA. Le laboratoire peut les vérifier à tout moment sur les pages indiquées. Les tarifs des fournisseurs pouvant évoluer, le forfait est révisable annuellement sur présentation des factures.

### 7.3 Contrat de maintenance corrective

**Montant : 1 948 500 FCFA par an**, soit **162 375 FCFA par mois**, facturé par trimestre, révisable à chaque reconduction annuelle.

Le contrat est un forfait annuel à périmètre et délais définis, forme usuelle pour une application métier critique. Il couvre la maintenance de la plateforme **sans décompte de temps** : c'est une obligation de résultat, quel que soit l'effort nécessaire.

**Inclus dans le forfait**

| Domaine | Prestations |
|---|---|
| Maintenance corrective | Analyse et correction de toute anomalie de la plateforme |
| Maintenance de sécurité | Application mensuelle des mises à jour de sécurité ; traitement des alertes de vulnérabilités ; maintien du critère « composants supportés » du TdR, y compris les montées de version majeures |
| Maintenance adaptative | Adaptation aux évolutions des services tiers : FluidPay, FluidInvoice et DGI, notifications mobiles, navigateurs, systèmes mobiles |
| Sauvegardes et reprise | Contrôle mensuel des sauvegardes ; test de restauration semestriel avec procès-verbal |
| Supervision | Traitement des alertes ; rapport mensuel de disponibilité |
| Assistance | Réponse aux questions des utilisateurs par e-mail et téléphone, en heures d'activité ; aide à la revue trimestrielle des comptes |
| Comptes utilisateurs | Création, modification et désactivation de comptes à la demande de la direction ; réinitialisations assistées |
| Suivi | Rapport mensuel de disponibilité ; rapport trimestriel d'activité (anomalies traitées, mises à jour appliquées, disponibilité mesurée) ; revue semestrielle des journaux de sécurité ; réunion annuelle de bilan avec mise à jour du présent rapport |

**Hors forfait**

Toute demande qui ne relève pas de la maintenance est facturée **65 000 FCFA par jour**, avec un **minimum d'une demi-journée (32 500 FCFA) par intervention**, quelle que soit sa durée. Chaque demande fait l'objet d'une estimation en demi-journées, validée par le laboratoire avant réalisation. Sont concernés, par exemple : l'ajout d'un champ, d'un filtre ou d'un export, la modification d'un modèle de compte rendu ou de facture, un nouveau type d'examen ou de circuit de validation, une nouvelle fonctionnalité ou un nouveau module, une intégration avec un système tiers, la formation collective des utilisateurs, la reprise ou l'export de données vers un autre système, les interventions sur site hors Cotonou.

**Engagements de délais**, en heures d'activité du laboratoire (lundi–vendredi 7 h 30–18 h 30, samedi 8 h–13 h) :

| Niveau | Définition | Exemple | Prise en charge | Rétablissement |
|---|---|---|---|---|
| P1 – Critique | Plateforme inaccessible ou suspicion de fuite de données | Impossible de se connecter ; alerte de sécurité | 1 h | 4 h |
| P2 – Majeur | Fonction essentielle bloquée, sans contournement | Validation ou impression des comptes rendus impossible ; facturation bloquée | 2 h | 8 h |
| P3 – Mineur | Fonction dégradée, contournement possible | Erreur sur un écran secondaire ; notification non envoyée | 4 h | 3 jours ouvrés |
| P4 – Faible | Gêne sans incidence sur l'activité | Affichage, libellé, confort | 1 jour ouvré | Prochaine mise à jour planifiée |

Le délai de rétablissement de 4 heures pour un incident critique correspond au délai maximal de reprise fixé par le TdR. Les délais sont suspendus hors heures d'activité ; un incident critique signalé un samedi après 13 h est pris en charge le lundi à 7 h 30, sauf astreinte convenue séparément.

**Signalement** : par e-mail à l'adresse de support et, pour les incidents critiques, par téléphone. Chaque signalement reçoit un numéro de suivi.

### 7.4 Modules optionnels

| Module | Tarif | Base de calcul |
|---|---|---|
| Facturation normalisée (FluidInvoice, DGI) | 20 000 FCFA / mois | Forfait |
| Envoi de SMS (avis au patient, lien de facture) | 25 FCFA / SMS | Volume mensuel |
| Notification par appel vocal en langue locale | 3 FCFA / seconde | Nombre d'appels × durée moyenne |

Exemple de calcul des appels vocaux, à titre d'illustration : 300 appels par mois d'une durée moyenne de 30 secondes = 9 000 secondes × 3 FCFA = **27 000 FCFA / mois**.

---

## 8. Risques résiduels et recommandations

### 8.1 Risques résiduels techniques

| Risque | Mesure compensatoire |
|---|---|
| Brève interruption (moins d'une minute) à chaque mise à jour | Mises à jour hors heures ouvrées ; retour arrière automatique |
| Un compte rendu livré reste modifiable par un pathologiste signataire | Version précédente conservée, motif obligatoire, alerte aux administrateurs, journaux inaltérables. Alternative possible, sur demande du laboratoire : interdiction stricte et circuit d'addendum |

### 8.2 Recommandations organisationnelles à l'attention du laboratoire

| Recommandation | Fonction du TdR |
|---|---|
| Formaliser la procédure d'attribution et de retrait des accès, notamment au départ d'un employé, et la revue trimestrielle des comptes ; le journal d'accès et l'écran des utilisateurs permettent cette revue | F1 |
| Désigner un responsable du signalement des incidents à la direction (délai de 24 h) et rédiger la procédure ; les alertes techniques lui sont adressées | F6 |
| Effectuer les formalités auprès de l'APDP : registre des traitements, déclaration, droits des personnes | Conformité |
| Vérifier le pays d'hébergement des serveurs au regard des règles de transfert du Livre V, et le documenter dans le registre | Conformité |
| Sensibiliser le personnel : hameçonnage, code à usage unique, verrouillage des postes | F1 |
| Définir la durée de conservation des dossiers et des fichiers | F2, coûts |

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
