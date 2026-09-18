#!/usr/bin/env python3
"""Faux serveur FluidPay SMS, pour essayer la chaîne complète sans clé ni SMS réel.

Imite POST /api/v1/sms tel que le décrit docs/openapi.yaml : mêmes champs
obligatoires, mêmes bornes, même déduplication sur source_id, mêmes réponses
200 / 422. Affiche chaque requête reçue, ce qui rend visible le payload que le
backend envoie vraiment.

    python3 tools/fake-fluidpay-sms.py            # port 8099
    python3 tools/fake-fluidpay-sms.py --port 9000
    python3 tools/fake-fluidpay-sms.py --panne    # répond 500, pour voir que
                                                  # l'encaissement reste acquis

Le corps est chiffré comme sur le vrai serveur : AES-256-CBC, base64(IV+chiffré),
avec `messages` à l'intérieur du clair. Le script déchiffre et affiche.

Puis, côté backend :
    FLUIDPAY_SMS_BASE_URL=http://localhost:8099
    FLUIDPAY_SMS_API_KEY=peu-importe-mais-non-vide
    FLUIDPAY_SMS_ENCRYPTION_KEY=<la même clé 32 caractères que --cle>
"""
import argparse
import base64
import json
import os
import sys
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

# Bornes du contrat publié (docs/openapi.yaml, POST /api/v1/sms).
CHAMPS_OBLIGATOIRES = ("provider", "recipient_phone", "message",
                       "reference_id", "source_id", "source_type")
REFERENCE_MIN, REFERENCE_MAX = 15, 50
MESSAGE_MAX, SENDER_MAX = 1600, 11

# source_id déjà vus : c'est la déduplication du vrai FluidPay.
DEJA_VUS = set()
PANNE = False
CLE = None  # clé de chiffrement, 32 caractères


def dechiffrer(encrypted_data: str) -> dict:
    """Déchiffre comme le vrai serveur : base64(IV + chiffré), AES-256-CBC.

    La clé est prise telle quelle en octets — la décoder depuis l'hexadécimal
    donnerait 16 octets, et le vrai serveur répond alors decryption_error.
    """
    brut = base64.b64decode(encrypted_data)
    iv, chiffre = brut[:16], brut[16:]
    d = Cipher(algorithms.AES(CLE.encode()), modes.CBC(iv)).decryptor()
    rembourre = d.update(chiffre) + d.finalize()
    u = padding.PKCS7(128).unpadder()
    return json.loads(u.update(rembourre) + u.finalize())

VERT, ROUGE, JAUNE, GRIS, RAZ = "\033[32m", "\033[31m", "\033[33m", "\033[90m", "\033[0m"


class Handler(BaseHTTPRequestHandler):

    # HTTP/1.1 : sans cela, un client qui découpe son corps en morceaux
    # (Transfer-Encoding: chunked) se voit répondre avant même d'avoir fini
    # d'émettre, et le corps arrive vide.
    #
    # Le revers : HTTP/1.1 garde la connexion ouverte. Sur un serveur à un seul
    # fil, la première connexion monopolise alors le service et plus rien
    # n'est accepté ensuite — d'où ThreadingHTTPServer, et le Connection: close
    # posé sur chaque réponse.
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass  # on journalise nous-mêmes, plus lisiblement

    def _lire_corps(self) -> bytes:
        """Lit le corps, que le client annonce sa taille ou l'émette en morceaux.

        RestTemplate n'envoie pas toujours de Content-Length : selon la fabrique
        de requêtes, il passe en Transfer-Encoding: chunked, et une lecture
        naïve rend alors une chaîne vide.
        """
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            morceaux = []
            while True:
                entete = self.rfile.readline().strip()
                if not entete:
                    break
                taille = int(entete.split(b";")[0], 16)
                if taille == 0:
                    self.rfile.readline()  # ligne vide de fin
                    break
                morceaux.append(self.rfile.read(taille))
                self.rfile.readline()  # CRLF après chaque morceau
            return b"".join(morceaux)
        return self.rfile.read(int(self.headers.get("Content-Length") or 0))

    def _repondre(self, code, charge):
        corps = json.dumps(charge, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(corps)))
        # Une connexion par requête : le backend en ouvre peu, et une connexion
        # laissée ouverte immobiliserait un fil pour rien.
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(corps)

    def do_POST(self):
        if self.path.rstrip("/") != "/api/v1/sms":
            self._repondre(404, {"success": False, "message": "Unknown endpoint"})
            return

        brut = self._lire_corps()
        print(f"\n{GRIS}{'─' * 72}{RAZ}")

        autorisation = self.headers.get("Authorization", "")
        print(f"Authorization : {autorisation[:24]}…" if autorisation else
              f"{ROUGE}Authorization : ABSENT — le backend n'envoie pas la clé{RAZ}")
        print(f"User-Agent    : {self.headers.get('User-Agent', ROUGE + 'ABSENT' + RAZ)}")

        try:
            charge = json.loads(brut)
        except json.JSONDecodeError:
            print(f"{ROUGE}Corps illisible : {brut[:200]}{RAZ}")
            # Sans le corps on ne peut rien conclure : montrer les en-têtes reçus
            # dit au moins comment le client a annoncé sa charge utile.
            print(f"{GRIS}En-têtes reçus :{RAZ}")
            for cle, valeur in self.headers.items():
                print(f"{GRIS}  {cle}: {valeur}{RAZ}")
            self._repondre(400, {"success": False, "message": "Invalid JSON"})
            return

        # Le vrai serveur n'accepte que du chiffré : un « messages » posé à côté
        # est ignoré, et il répond que le champ est manquant.
        if "encrypted_data" not in charge:
            print(f"{ROUGE}✗ encrypted_data absent — le vrai serveur refuserait "
                  f"(missing_encrypted_data){RAZ}")
            self._repondre(422, {"success": False, "error": {
                "error_code": "missing_encrypted_data",
                "message": "Le champ encrypted_data est requis."}})
            return

        try:
            charge = dechiffrer(charge["encrypted_data"])
        except Exception as e:
            print(f"{ROUGE}✗ déchiffrement impossible ({e}) — clé différente de "
                  f"celle du backend ?{RAZ}")
            self._repondre(422, {"success": False, "error": {
                "error_code": "decryption_error",
                "message": "Impossible de déchiffrer le payload."}})
            return

        print(f"{GRIS}(déchiffré){RAZ}")
        print(json.dumps(charge, indent=2, ensure_ascii=False))

        for champ in ("nonce", "timestamp"):
            if champ not in charge:
                print(f"{ROUGE}✗ {champ} absent du clair — exigé contre le rejeu{RAZ}")
                self._repondre(422, {"success": False,
                                     "message": f"The {champ} field is required."})
                return

        messages = charge.get("messages")
        if not isinstance(messages, list) or not messages:
            print(f"{ROUGE}✗ « messages » absent ou vide{RAZ}")
            self._repondre(422, {"success": False, "message": "The messages field is required."})
            return

        # Contrôles du contrat : ce que le vrai FluidPay refuserait.
        for m in messages:
            for champ in CHAMPS_OBLIGATOIRES:
                if not m.get(champ):
                    print(f"{ROUGE}✗ champ obligatoire manquant : {champ}{RAZ}")
                    self._repondre(422, {"success": False,
                                         "message": f"The {champ} field is required."})
                    return
            reference = m["reference_id"]
            if not REFERENCE_MIN <= len(reference) <= REFERENCE_MAX:
                print(f"{ROUGE}✗ reference_id de {len(reference)} caractères "
                      f"(attendu {REFERENCE_MIN}-{REFERENCE_MAX}){RAZ}")
                self._repondre(422, {"success": False,
                                     "message": "The reference_id field is invalid."})
                return
            if len(m["message"]) > MESSAGE_MAX:
                print(f"{ROUGE}✗ message de {len(m['message'])} caractères "
                      f"(max {MESSAGE_MAX}){RAZ}")
                self._repondre(422, {"success": False,
                                     "message": "The message field is too long."})
                return
            if m.get("sender") and len(m["sender"]) > SENDER_MAX:
                print(f"{ROUGE}✗ sender de {len(m['sender'])} caractères "
                      f"(max {SENDER_MAX}){RAZ}")
                self._repondre(422, {"success": False,
                                     "message": "The sender field is too long."})
                return

        if PANNE:
            print(f"{JAUNE}→ 500 simulé : l'avis ne part pas, mais le geste métier "
                  f"(encaissement, validation) doit rester acquis{RAZ}")
            self._repondre(500, {"success": False, "message": "Internal error"})
            return

        nouveaux = [m for m in messages if m["source_id"] not in DEJA_VUS]
        if not nouveaux:
            print(f"{JAUNE}→ 422 doublon : ce source_id est déjà passé. "
                  f"C'est le comportement attendu pour un SMS de facture rejoué.{RAZ}")
            self._repondre(422, {"success": False,
                                 "message": "All messages are duplicates of existing active records"})
            return

        for m in nouveaux:
            DEJA_VUS.add(m["source_id"])
            print(f"{VERT}→ SMS ACCEPTÉ vers {m['recipient_phone']} "
                  f"(provider={m['provider']}, source_type={m['source_type']}){RAZ}")
            print(f'{VERT}   « {m["message"]} »{RAZ}')

        self._repondre(200, {"success": True, "data": {
            "message": "SMS dispatch initiated",
            "messages_count": len(nouveaux),
            "batch_id": str(uuid.uuid4()),
        }})


if __name__ == "__main__":
    # Affichage immédiat, même redirigé vers un fichier.
    sys.stdout.reconfigure(line_buffering=True)

    parseur = argparse.ArgumentParser(description=__doc__)
    parseur.add_argument("--port", type=int, default=8099)
    parseur.add_argument("--panne", action="store_true",
                         help="répondre 500 à tout, pour vérifier que l'échec est absorbé")
    parseur.add_argument("--cle", default=os.environ.get("FLUIDPAY_SMS_ENCRYPTION_KEY", ""),
                         help="clé de chiffrement, 32 caractères (celle du backend). "
                              "Par défaut : $FLUIDPAY_SMS_ENCRYPTION_KEY")
    options = parseur.parse_args()
    PANNE = options.panne
    CLE = options.cle
    if not CLE:
        parseur.error("clé de chiffrement absente : passer --cle, ou sourcer le .env "
                      "qui définit FLUIDPAY_SMS_ENCRYPTION_KEY")
    if len(CLE) != 32:
        parseur.error(f"la clé doit faire 32 caractères ({len(CLE)} reçus)")

    print(f"Faux FluidPay SMS sur http://localhost:{options.port}/api/v1/sms"
          + (f"  {JAUNE}[MODE PANNE]{RAZ}" if PANNE else ""))
    print(f"{GRIS}Backend : FLUIDPAY_SMS_BASE_URL=http://localhost:{options.port} "
          f"FLUIDPAY_SMS_API_KEY=test FLUIDPAY_SMS_ENCRYPTION_KEY={CLE}{RAZ}")
    ThreadingHTTPServer(("127.0.0.1", options.port), Handler).serve_forever()
