# Écarts entre le schéma Laravel et le schéma Spring Boot

Établi le 7 août 2026, en comparant :

- le **dump de production Laravel** (`fzbnpjhy_caapgestion.sql`, phpMyAdmin, MariaDB 10.5) — 85 tables, 852 colonnes ;
- le **schéma PostgreSQL de production** (`pg_dump --schema-only`) — 79 tables, 980 colonnes.

Le schéma PostgreSQL correspond table pour table aux 74 entités JPA, ce que l'application
vérifie elle-même à chaque démarrage (`ddl-auto: validate`).

---

## 1. Tables de Spring absentes du dump Laravel

### 1.1 Renommages — la donnée existe sous un autre nom

Les migrations Flyway documentent elles-mêmes trois de ces quatre correspondances.

| Spring | Laravel | Lignes | Source de la correspondance |
|---|---|---|---|
| `cashbox_operations` | `cashbox_adds` | 13 370 | V28 : « anciennement cashbox_adds dans Laravel » |
| `lab_tests` | `tests` | 269 | colonnes correspondantes |
| `details_contrats` | `details__contrats` | 189 | V1 : « renommé details__contrats en V3 — typo Laravel préservée » |
| `title_reports` | `report_titles` | 2 | V3 : `ALTER TABLE title_reports RENAME TO report_titles` |

### 1.2 Tables réellement nouvelles

Aucune contrepartie Laravel. Elles démarreront vides, ce qui est le comportement attendu.

| Table | Origine |
|---|---|
| `bank_deposits` | V31 — « transfert caisse vente → banque » |
| `cashbox_vouchers` | V29 — bons de caisse, *distincts* de `cashbox_tickets` |
| `cashbox_voucher_details` | V29 |
| `consultation_files` | — |
| `revoked_tokens` | révocation de jetons JWT, purement technique |

---

## 2. Les couples dormants

**C'est le principal risque de la reprise.** Dans ces quatre tables, la colonne qui porte le
même nom qu'en Laravel n'est **pas** celle que le code lit. Une reprise qui apparie les
colonnes par leur nom remplit la colonne héritée et laisse la colonne vivante à sa valeur
par défaut — sans qu'aucune erreur ne soit levée.

| Table | Colonne Laravel | Colonne lue par le code | Lignes | Conséquence d'un mauvais appariement |
|---|---|---|---|---|
| `reports` | `description` | `content` | 18 996 | comptes rendus au corps vide ; le PDF imprime `${content}` |
| `invoice_details` | `price` | `unit_price` | 18 193 | lignes de facture à prix unitaire zéro |
| `invoices` | `status_invoice` (entier) | `status` (énumération) | 17 050 | toutes les factures à « PENDING » |
| `payments` | `payment_amount` (chaîne) | `amount` (décimal) | 6 | paiements à zéro |

### Preuves

- `reports` : `PdfReportServiceImpl` pose `ctx.setVariable("content", report.getContent())`,
  et `ReportRequestDto` n'expose que `content` — `description` n'y figure pas.
- `invoices` : le schéma de production porte `status_invoice integer NOT NULL` **et**
  `status character varying(20) NOT NULL`.
- `payments` : `Payment.java` déclare `amount` en `BigDecimal NOT NULL` et
  `payment_amount` en `String`.
- `invoice_details` : `price` en `Double` nullable, `unit_price` en `BigDecimal NOT NULL`.

### Point ouvert

La correspondance des statuts de facture n'est pas déterminable depuis le code : que valent
0, 1, 2 dans `status_invoice` ? Elle doit être fournie par le métier avant toute reprise.

---

## 3. Ce qui a été vérifié, et comment

| Affirmation | Méthode |
|---|---|
| Le schéma PostgreSQL correspond aux entités | `pg_dump --schema-only` comparé à `V1__baseline_from_entities.sql` ; aucune table d'écart |
| Aucune entité modifiée depuis la génération de la baseline | `git log` sur les fichiers portant `@Entity` |
| Les 12 tables orphelines ne sont utilisées par rien | aucune `@Table`, et aucune référence dans les requêtes natives ou JPQL |
| Le dump ne contient pas de mojibake | 0 occurrence de `Ã©`, `â€™`, `Â` sur 51 447 lignes contenant « é » |
| `employees.salary` est une colonne vestigiale | commentaire de `EmployeeServiceImpl:62` ; le formulaire ne la demande pas |

Le détail colonne par colonne est dans `migration-laravel/analyse/correspondance.md`
(48 renommages, 1 cas ambigu, 66 colonnes sans destination).
