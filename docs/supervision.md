# Supervision et détection (N-09)

Le cahier des charges demande 99 % de disponibilité en heures ouvrées, une
alerte en cas d'indisponibilité et une alerte sur les tentatives répétées. Ce
document décrit les quatre pièces qui y répondent, et ce qui reste à faire à la
main sur les serveurs.

| Pièce | Où | Ce qu'elle fait |
|---|---|---|
| Sonde externe | Uptime Kuma, **sur une autre machine** | Teste l'API et le front toutes les minutes, alerte après deux échecs, mesure la disponibilité |
| Alertes applicatives | l'API elle-même (`common/supervision`) | Courriel sur rafale de connexions ratées, de verrouillages, d'erreurs 5xx, et disque presque plein |
| Métriques | `/actuator/prometheus`, Prometheus + Grafana sur le serveur | Historique 90 jours : temps de réponse par route, erreurs, disque, sauvegardes |
| Rapport mensuel | script SQL sur la base d'Uptime Kuma | Disponibilité du mois, heures ouvrées seulement |

---

## 1. Sonde externe — Uptime Kuma

La sonde doit tourner **ailleurs que sur le serveur de l'API** : un serveur qui
tombe ne peut pas signaler qu'il est tombé. Un petit VPS, ou un poste du
prestataire allumé en permanence, suffit (512 Mo de RAM).

### Installation

```yaml
# docker-compose.yml, sur la machine de supervision
services:
  uptime-kuma:
    image: louislam/uptime-kuma:1
    container_name: uptime-kuma
    restart: unless-stopped
    volumes:
      - ./uptime-kuma-data:/app/data
    ports:
      - "127.0.0.1:3001:3001"
```

`docker compose up -d`, puis ouvrir `http://localhost:3001` (par tunnel SSH :
`ssh -L 3001:localhost:3001 supervision`) et créer le compte administrateur.

### Les deux moniteurs

Même réglage pour les deux : type **HTTP(s) – Keyword**, intervalle **60**
secondes, « Retries » **2** avec « Heartbeat Retry Interval » 60 secondes — la
sonde n'alerte donc qu'après deux échecs consécutifs, soit deux minutes
d'indisponibilité, pas sur un simple paquet perdu.

| Nom | URL | Mot-clé attendu |
|---|---|---|
| API | `https://api.caap.bj/actuator/health` | `"status":"UP"` |
| Front | l'adresse de la page de connexion du front (`https://new.caap.bj/login`, à confirmer) | un mot présent dans la page de connexion, par exemple `Connexion` |

`/actuator/health` est public, sans jeton (voir `SecurityConfig`), et ne révèle
rien d'autre que `UP`/`DOWN`. Il reste `UP` même si le SMTP est injoignable :
l'indicateur mail est éteint volontairement (`management.health.mail.enabled:
false`), sinon la sonde déclarerait l'API indisponible pour une panne de
courrier.

### Notifications

Réglages → Notifications → « Setup Notification », puis cocher « Default
enabled » et « Apply on all existing monitors ».

**E-mail (SMTP)** : type *Email (SMTP)*, avec le même serveur SMTP que l'API
(`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` du `.env`). Mettre
en destinataires le prestataire et les administrateurs du laboratoire.

**SMS** : Uptime Kuma sait appeler un webhook, mais la passerelle FluidPay
exige un corps chiffré en AES avec la clé du tableau de bord
(`FluidPaySmsClient`, `FluidPayPayloadCipher`) ; le webhook d'Uptime Kuma ne
sait pas chiffrer. **Le SMS n'est donc pas branché : l'alerte part par e-mail
seul.** Deux voies si le SMS est exigé :

- *Telegram* (gratuit, natif dans Uptime Kuma) : une notification sur le
  téléphone, immédiate, sans passerelle ;
- un mini-relais HTTP sur la machine de supervision (une dizaine de lignes :
  reçoit le webhook en clair, chiffre comme `FluidPayPayloadCipher`, appelle
  `POST /api/v1/sms` de FluidPay). À écrire seulement si Telegram est refusé.

### Rapport mensuel en heures ouvrées

Uptime Kuma affiche une disponibilité 24 h/24 ; il ne sait pas la restreindre
aux heures ouvrées (lundi–vendredi 7 h 30–18 h 30, samedi 8 h–13 h). Plutôt
qu'une page de statut ou un export à la main, la méthode la plus simple est de
lire sa base SQLite, qui garde chaque sonde avec son horodatage :

```sh
# sur la machine de supervision, sqlite3 installé (apt install sqlite3)
infra/uptime-kuma/disponibilite-mensuelle.sh 2026-10 ./uptime-kuma-data/kuma.db
```

```
moniteur  sondes  echecs  disponibilite_pct
--------  ------  ------  -----------------
API       14520   7       99.95
Front     14520   0       100.0
```

Le script filtre sur les jours et heures ouvrés, en heure de Porto-Novo
(Uptime Kuma horodate en UTC), et ignore les sondes « en attente » et
« maintenance ». Uptime Kuma conserve les sondes 180 jours par défaut
(Réglages → Monitor History) : lancer le script dans les deux mois.

**Exemple de vérification humaine** : arrêter l'API trois minutes
(`docker stop labo-api`, attendre, `docker start labo-api`) ; l'e-mail « Down »
doit arriver dans les deux à trois minutes, puis « Up » au redémarrage.

---

## 2. Alertes applicatives (dans l'API)

Trois composants dans `com.labo.anapath.common.supervision` :

- **`CompteurDAlertes`** — compteurs en mémoire, par minute, de trois
  événements : `echecDeConnexion()` (mot de passe refusé, compte désactivé,
  code à usage unique faux), `verrouillage()` (compte verrouillé après trop de
  codes faux), `erreurServeur()` (réponse HTTP ≥ 500). Un redémarrage les remet
  à zéro.
- **`FiltreDErreursServeur`** — filtre servlet le plus extérieur, qui compte
  les statuts ≥ 500 et les exceptions échappées.
- **`SurveillanceDesSeuils`** — tâche toutes les 10 minutes (`0 */10 * * * *`,
  zone `Africa/Porto-Novo`) qui compare aux seuils et envoie un courriel en
  texte brut (`EmailService.sendAlerteSupervision`).

| Seuil | Fenêtre | Destinataires |
|---|---|---|
| plus de **20** échecs de connexion ou de code, toutes adresses confondues | 10 min | `admin_mails` + `ALERT_EMAIL` |
| plus de **5** verrouillages de comptes | 1 h | `admin_mails` + `ALERT_EMAIL` |
| plus de **10** réponses 5xx | 5 min | `ALERT_EMAIL` |
| moins de **15 %** d'espace libre sur `app.storage.path` ou sur `/` | à chaque passage | `ALERT_EMAIL` |

Les fenêtres sont glissantes à la minute près : une rafale survenue au début
d'un intervalle de dix minutes est vue au passage suivant. **Un même seuil
n'écrit pas plus d'une fois par heure** ; une attaque qui dure ne produit pas
six courriels par heure.

Destinataires :

- `admin_mails` — réglage de l'écran Paramètres (clé `admin_mails` de
  `setting_apps`, adresses séparées par `|`, `;` ou `,`), toutes agences
  confondues ;
- `ALERT_EMAIL` — variable d'environnement, dans le `.env` du serveur
  (`docker-compose.yml` la transmet). Vide : les alertes de sécurité partent
  quand même aux administrateurs, les alertes techniques (5xx, disque) sont
  seulement journalisées (`WARN`).

**Que faire à la réception.** Rafale de connexions : ouvrir le journal des
accès, repérer l'adresse ou le compte visé ; si c'est un collègue qui s'acharne
sur un mot de passe oublié, pas d'action ; sinon bloquer l'adresse sur nginx.
Erreurs 5xx : `docker logs labo-api --since 10m | grep ERROR`. Disque :
`docker system df`, vieux journaux, vieilles sauvegardes (`APP_BACKUP_KEEP`).

**Exemple de vérification humaine** : 25 connexions ratées en moins de dix
minutes, puis attendre le passage de la tâche (au plus dix minutes) :

```sh
for i in $(seq 1 25); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST https://api.caap.bj/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d '{"email":"inconnu@exemple.bj","password":"faux-mot-de-passe"}'
  sleep 13   # la limitation par adresse laisse passer 5 tentatives par minute
done
```

Le `RateLimitFilter` répond 429 au-delà de cinq essais par minute et par
adresse ; ces 429 ne comptent pas comme des échecs (ils n'atteignent pas la
connexion). D'où la pause de treize secondes : 25 essais en un peu moins de six
minutes, tous comptés.

---

## 3. Métriques — Prometheus et Grafana

### Côté API

`/actuator/metrics` et `/actuator/prometheus` **n'existent pas par défaut**
(404). Ils s'allument avec `MANAGEMENT_METRICS_EXPOSED=true` dans le `.env`, et
ne doivent l'être que sur un serveur où un Prometheus les lit. Trois garde-fous
les tiennent hors d'Internet :

1. **Désactivés par défaut** (`management.endpoint.*.enabled`).
2. **`SecurityConfig`** : tout `/actuator/**` sauf `health` n'est servi qu'à
   une adresse privée ou de boucle locale (`127/8`, `::1`, `10/8`, `172.16/12`,
   `192.168/16`) — le réseau Docker, donc. L'API lit l'adresse du client dans
   `X-Forwarded-For` (`server.forward-headers-strategy: framework`) : **nginx
   doit la poser depuis `$remote_addr`**, jamais recopier celle que le client
   envoie, sinon un client extérieur se ferait passer pour le réseau interne.
3. **nginx** ne relaie pas `/actuator/` hors `health`. Dans
   `infra/nginx/api.caap.bj.conf` (PR #129), à l'intérieur du `server` :

   ```nginx
   location = /actuator/health {
       proxy_pass http://127.0.0.1:7001;
       proxy_set_header X-Forwarded-For $remote_addr;
       proxy_set_header X-Forwarded-Proto $scheme;
   }
   location /actuator/ {
       allow 127.0.0.1;
       deny all;
   }
   ```

Pour le p95 par route, `application.yml` publie l'histogramme des temps de
réponse (`percentiles-histogram.http.server.requests: true`). Deux jauges
maison complètent Spring Boot (`JaugesDeSupervision`) :
`labo_disque_libre_pourcent{volume=...}` pour le stockage et la racine, et
`labo_sauvegarde_derniere_epoch_secondes` (date du dernier `backup-*.sql`).

### Lancer Prometheus et Grafana

Sur le serveur de l'API, dans le dossier du dépôt :

```sh
# .env : MANAGEMENT_METRICS_EXPOSED=true  GRAFANA_ADMIN_PASSWORD=...
docker compose -f docker-compose.yml -f docker-compose.supervision.yml up -d
```

Les deux fichiers forment un seul projet Compose : Prometheus joint l'API par
son nom de service (`api:7001`, voir `infra/prometheus/prometheus.yml`, à
aligner sur `APP_PORT`). Conservation **90 jours**
(`--storage.tsdb.retention.time=90d`). Prometheus (`:9090`) et Grafana
(`:3000`) n'écoutent que sur la boucle locale de l'hôte : y accéder par tunnel
SSH, jamais par nginx.

Dans Grafana : Connections → Data sources → Prometheus, URL
`http://prometheus:9090`. Puis un tableau de bord avec les panneaux ci-dessous.

### Requêtes PromQL du tableau de bord

| Panneau | Requête |
|---|---|
| Disponibilité (30 j, vue de Prometheus) | `avg_over_time(up{job="labo-api"}[30d]) * 100` |
| Requêtes par seconde | `sum(rate(http_server_requests_seconds_count{job="labo-api"}[5m]))` |
| p95 par route | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{job="labo-api"}[5m])))` |
| p95 global | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="labo-api"}[5m])))` |
| Erreurs 5xx par route | `sum by (uri) (rate(http_server_requests_seconds_count{job="labo-api", status=~"5.."}[5m]))` |
| Taux d'erreur (%) | `100 * sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` |
| Disque libre (%) | `labo_disque_libre_pourcent` (un trait par `volume` ; `-1` = volume absent) |
| Âge de la dernière sauvegarde (h) | `(time() - labo_sauvegarde_derniere_epoch_secondes) / 3600` |
| Mémoire JVM | `jvm_memory_used_bytes{area="heap"}` et `jvm_memory_max_bytes{area="heap"}` |
| Connexions base actives | `hikaricp_connections_active` / `hikaricp_connections_max` |
| Threads Tomcat occupés | `tomcat_threads_busy_threads` |

Alertes Grafana utiles (Alerting → Alert rules), en plus des courriels de
l'API : sauvegarde de plus de 36 h, disque sous 15 %, `up == 0` cinq minutes.

La disponibilité « officielle » du mois reste celle de la sonde externe
(section 1) : Prometheus tourne sur le serveur et ne voit pas une coupure
réseau ou une panne d'hôte.

---

## 4. Ce qui reste à faire à la main

- Choisir la machine de supervision, y installer Uptime Kuma, créer les deux
  moniteurs et la notification SMTP (section 1). Confirmer l'URL de la page de
  connexion du front.
- Décider du second canal (Telegram) si le SMS est exigé.
- Serveur de l'API : `ALERT_EMAIL=<adresse du prestataire>` dans `.env`, puis
  redéployer. Ajouter `admin_mails` dans Paramètres s'il est vide.
- nginx : bloc `location /actuator/` (section 3) et `X-Forwarded-For` depuis
  `$remote_addr`.
- Si la métrique est voulue : `MANAGEMENT_METRICS_EXPOSED=true`, lancer
  `docker-compose.supervision.yml`, créer le tableau de bord Grafana.
- Vérifications après déploiement : arrêter le conteneur trois minutes
  (section 1), 25 connexions ratées (section 2).
- Le premier du mois : `disponibilite-mensuelle.sh` pour le rapport.
