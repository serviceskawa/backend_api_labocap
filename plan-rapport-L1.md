# Plan du rapport de prestation (L1)
## Renforcement de la sécurité de la plateforme de gestion du laboratoire CAAP

Ce plan sert de base à la rédaction du livrable L1 prévu par le TdR (§6). Il reprend la grille du TdR, c'est-à-dire les fonctions F1 à F6 et les critères de réussite du §2.3, et s'appuie sur les référentiels listés ci-dessous.

---

## Référentiels retenus

| Rôle dans le rapport | Référentiel |
|---|---|
| Cadre légal (obligatoire) | Code du numérique, Livre V (loi n° 2017-20, modifiée par la loi n° 2020-35) et exigences de l'APDP |
| Cadre des mesures de sécurité | ISO/IEC 27001:2022 et ISO/IEC 27002:2022 (mesures de l'Annexe A) |
| Lecture pour la direction | NIST CSF 2.0 (Gouverner, Identifier, Protéger, Détecter, Répondre, Rétablir) |
| Analyse de risques | ISO/IEC 27005:2022, en version simplifiée |
| Sécurité applicative | OWASP ASVS 4.0.3 (niveau 2) et OWASP Top 10 2021 |
| Cotation des vulnérabilités | CVSS v3.1 (Critique ≥ 9.0, Élevée 7.0–8.9, Moyenne 4.0–6.9, Faible < 4.0) |
| Méthode d'audit et de test | ISO 19011 et NIST SP 800-115 |
| Sauvegarde et reprise (RPO/RTO) | ISO 22301 |
| Données de santé | ISO/IEC 27701 et ISO 15189:2022 §7.11 |

---

## Plan

### 0. Fiche documentaire
Version, classification « Confidentiel », liste de diffusion, historique des versions, validations.

### 1. Synthèse pour la direction (2 pages au maximum)
- Verdict sur chacun des 4 critères de réussite (§2.3 du TdR)
- Tableau F1–F6 : niveau attendu, niveau atteint et écart, sur la partie technique
- Points clés sur le budget et sur les coûts récurrents à prévoir

### 2. Contexte, périmètre et méthode
- 2.1 Rappel du TdR : contexte, objectifs, enjeux
- 2.2 Périmètre de la prestation : application, données, hébergement, utilisateurs, organisation (recommandations)
- 2.3 Référentiels et méthode : ISO 27001/27002, OWASP ASVS et Top 10, CVSS, ISO 22301

### 3. État des lieux initial (ancienne plateforme : labo-anapath)
- 3.1 Inventaire des composants et date de fin de support : Laravel 8, PHP 8.1, MySQL/MariaDB, bibliothèques front et PDF
- 3.2 Vulnérabilités applicatives, classées selon l'OWASP Top 10 et cotées en CVSS (registre V-01 à V-21)
- 3.3 Hébergement, données et sauvegardes
- 3.4 **Conclusion : obsolescence structurelle.** Corriger la plateforme existante n'était pas viable, il fallait la redévelopper.

### 4. Décision de refonte et impact budgétaire
- 4.1 Options étudiées : corriger l'existant, migrer le framework, ou refondre la plateforme. Justification du choix.
- 4.2 Écart par rapport à l'enveloppe du TdR (1 150 000) et explication de cet écart

### 5. Nouvelle plateforme (labsys_api + labsys_front)
- 5.1 Architecture et composants, tous couverts par un support de sécurité de l'éditeur
- 5.2 Migration de MySQL vers PostgreSQL : méthode de reprise et contrôles de complétude des données
- 5.3 Stockage des fichiers sur AWS S3
- 5.4 **Refonte complète de l'interface (UI/UX)** : principes, parcours simplifiés, captures avant et après
- 5.5 Mesures de sécurité intégrées au logiciel, classées par F1 à F6 et rattachées aux mesures ISO 27002 et aux exigences ASVS
- 5.6 **Gains de performance** : mesures avant et après sur les écrans clés
- 5.7 Fonctionnalités conservées et **fonctionnalités nouvelles** (application mobile, appels via internet, notifications vocales en langue locale, facturation normalisée, SMS)

### 6. Vérification de l'efficacité
- 6.1 Contre-tests des vulnérabilités relevées dans la section 3
- 6.2 Vérification des exigences ASVS
- 6.3 Test de restauration : procès-verbal, RPO et RTO mesurés
- 6.4 Journalisation et détection (partie technique de F4 et F6)

### 7. Coûts récurrents d'exploitation
- 7.1 Pourquoi les coûts augmentent : comparaison entre l'ancienne et la nouvelle architecture
- 7.2 **Hébergement obligatoire :**
  - serveur dédié (application et PostgreSQL)
  - AWS S3 : volume stocké, requêtes, transfert sortant, avec une projection de croissance annuelle
  - serveur d'appels, nécessaire aux appels via internet depuis l'application mobile
- 7.3 **Maintenance corrective gérée** (forfait récurrent) :
  - mises à jour de sécurité des composants : framework, dépendances, système d'exploitation, PostgreSQL
  - veille des vulnérabilités et application des correctifs
  - correction des anomalies
  - surveillance des sauvegardes et test de restauration périodique
  - maintien de la conformité dans le temps
  - délais d'intervention selon la gravité
- 7.4 **Modules optionnels :**

  | Module | Tarif | Base de calcul |
  |---|---|---|
  | Facturation normalisée (FluidInvoice) | 20 000 / mois | Forfait |
  | Envoi de SMS | 25 / SMS | Volume mensuel |
  | Notification par appel vocal en langue locale | 3 F / seconde | Nombre d'appels × durée moyenne |

- 7.5 Tableau récapitulatif mensuel et annuel, en trois scénarios : minimal, standard, avec options
- 7.6 Ce qui fait varier les coûts : volume d'examens, durée de conservation des fichiers, nombre d'appels et de SMS

> La maintenance gérée répond directement au constat de départ du TdR (§1.2 : « technologies vieillissantes »). Sans elle, la nouvelle plateforme se retrouvera dans la même situation dans 3 à 4 ans.

### 8. Risques résiduels et recommandations au laboratoire
- Risques techniques restants
- Mesures organisationnelles recommandées, à la charge du laboratoire

### Annexes
- A. Matrice de traçabilité : chaque exigence du TdR reliée à sa réalisation et à sa preuve
- B. Registre des vulnérabilités, avant et après
- C. Procès-verbal du test de restauration
- D. Procès-verbal de recette L2
- E. Détail des calculs de coûts
- F. Glossaire

---

## Informations à fournir avant la rédaction

1. Coût mensuel du serveur dédié, du serveur d'appels et du forfait de maintenance
2. Volume actuel des fichiers sur S3, ou nombre de comptes rendus produits par mois
3. Estimation mensuelle du nombre d'appels vocaux (et de leur durée moyenne) et du nombre de SMS
4. Devise à utiliser (FCFA ?)
5. Existence d'un avenant ou d'un accord écrit pour le dépassement de budget
