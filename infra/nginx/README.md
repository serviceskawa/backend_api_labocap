# nginx et TLS sur le serveur de production

`api.caap.bj.conf` est la configuration de référence du reverse proxy de l'API.
Sur le serveur, elle vit dans `/etc/nginx/sites-available/api.caap.bj`, liée
dans `sites-enabled`. Le port 7001 n'est publié que sur 127.0.0.1 par le
compose : nginx est le seul chemin depuis Internet.

## Mettre en place ou mettre à jour

    sudo cp infra/nginx/api.caap.bj.conf /etc/nginx/sites-available/api.caap.bj
    sudo ln -sf /etc/nginx/sites-available/api.caap.bj /etc/nginx/sites-enabled/
    sudo nginx -t && sudo systemctl reload nginx

## Certificat (Let's Encrypt, renouvellement automatique)

    sudo apt install certbot python3-certbot-nginx
    sudo mkdir -p /var/www/certbot
    sudo certbot certonly --webroot -w /var/www/certbot -d api.caap.bj

Le paquet installe un minuteur systemd (`systemctl list-timers | grep certbot`)
qui renouvelle le certificat trente jours avant son terme. Pour que nginx
recharge le nouveau certificat :

    # /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
    #!/bin/sh
    systemctl reload nginx

Essai à blanc : `sudo certbot renew --dry-run`.

## Vérifier

- `curl -I https://api.caap.bj/actuator/health` : 200, en-tête
  `Strict-Transport-Security` présent.
- SSL Labs (ssllabs.com/ssltest) : note A attendue.
- Le front (`new.caap.bj`) a sa propre configuration, dans le dépôt du front.
