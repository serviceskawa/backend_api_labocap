# Secrets en fichier

Déposer ici le compte de service Firebase, puis renseigner dans `.env` :

    PUSH_CREDENTIALS_FILE=/var/lib/labo/secrets/firebase.json

Le chemin est celui **dans le conteneur** : le dossier y est monté sur
`/var/lib/labo/secrets`.

Sans ce fichier le serveur démarre et sert normalement ; il écrit une ligne
au démarrage — « Notifications hors-app inactives » — et les badges de
non-lus continuent de fonctionner dans l'application ouverte.

Ce fichier ne doit exister que sur le serveur. En perdre une copie revient à
donner le droit d'envoyer des notifications au nom du laboratoire.
