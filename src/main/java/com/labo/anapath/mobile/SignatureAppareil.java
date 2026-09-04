package com.labo.anapath.mobile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Vérifie qu'un acte a bien été signé par un appareil enrôlé.
 *
 * <h2>Ce que la signature prouve, et ce qu'elle ne prouve pas</h2>
 *
 * <p>Elle prouve que la clé privée d'un appareil donné a signé ce condensé-là.
 * Cette clé est produite dans l'enclave sécurisée du téléphone — Keychain sur
 * iOS, Keystore sur Android — et n'en sort jamais : elle n'est ni exportable ni
 * lisible, même par le propriétaire de l'appareil. Un jeton volé ne suffit donc
 * pas à fabriquer une signature valide.</p>
 *
 * <p>Elle ne prouve pas <em>qui</em> tenait le téléphone. C'est le rôle de la
 * biométrie qui déverrouille la clé côté appareil, et du PIN qui ouvre la
 * session. Les trois ensemble valent bien mieux qu'une session web ouverte.</p>
 *
 * <h2>Le condensé signé</h2>
 *
 * <p>{@code validate:<reportId>:<userId>:<signedAt>} — le compte-rendu visé,
 * l'auteur, et l'instant. Signer l'instant est ce qui empêche de rejouer une
 * signature capturée sur un autre dossier : elle ne vaut que pour celui-là, et
 * seulement quelques minutes.</p>
 */
@Slf4j
@Component
public class SignatureAppareil {

    /**
     * Écart toléré entre l'horodatage signé et l'heure du serveur.
     *
     * <p>Assez large pour absorber une horloge de téléphone mal réglée et une
     * connexion lente au comptoir ; assez étroit pour qu'une signature
     * interceptée ne serve pas longtemps.</p>
     */
    private static final Duration FENETRE = Duration.ofMinutes(5);

    /**
     * Format d'horodatage du condensé, fixé explicitement.
     *
     * <p>Ne jamais employer {@code LocalDateTime.toString()} ici : il <b>omet les
     * secondes lorsqu'elles valent zéro</b> et ajoute les nanosecondes lorsqu'il
     * y en a. Le client, lui, écrit un format constant. Une signature sur
     * soixante aurait donc échoué — au hasard, une fois par minute —, et le
     * défaut aurait été très pénible à reproduire.</p>
     *
     * <p>Ce format fait partie du contrat avec l'application : le modifier
     * invaliderait les signatures des versions déjà déployées.</p>
     */
    private static final java.time.format.DateTimeFormatter HORODATAGE =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Compose le condensé à signer. La même méthode sert des deux côtés — le
     * client Flutter la reproduit à l'identique — donc tout changement de format
     * invaliderait les signatures des versions déjà déployées.
     */
    public String condense(UUID reportId, UUID userId, LocalDateTime signedAt) {
        return "validate:" + reportId + ":" + userId + ":" + HORODATAGE.format(signedAt);
    }

    /**
     * @param clePubliqueBase64 clé publique X.509 transmise à l'enrôlement
     * @param message           condensé recomposé par le serveur, jamais celui fourni par le client
     * @param signatureBase64   signature produite par l'appareil
     */
    public boolean verifier(String clePubliqueBase64, String message, String signatureBase64) {
        try {
            byte[] octetsCle = Base64.getDecoder().decode(clePubliqueBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(octetsCle);

            // L'algorithme se déduit de la clé plutôt que d'être annoncé par le
            // client : le laisser choisir ouvrirait la porte à une signature
            // vérifiée avec un algorithme plus faible que celui d'origine.
            PublicKey cle;
            String algorithme;
            try {
                cle = KeyFactory.getInstance("EC").generatePublic(spec);
                algorithme = "SHA256withECDSA";
            } catch (Exception ecEchoue) {
                cle = KeyFactory.getInstance("RSA").generatePublic(spec);
                algorithme = "SHA256withRSA";
            }

            Signature verificateur = Signature.getInstance(algorithme);
            verificateur.initVerify(cle);
            verificateur.update(message.getBytes(StandardCharsets.UTF_8));
            return verificateur.verify(Base64.getDecoder().decode(signatureBase64));

        } catch (Exception e) {
            // Clé illisible, signature mal formée, algorithme inconnu : tout se
            // solde par un refus. On journalise sans le détail — il renseignerait
            // qui cherche à deviner la forme attendue.
            log.warn("Signature d'appareil rejetée : {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * L'horodatage signé est-il assez frais ?
     *
     * <p>Vérifié dans les deux sens : une signature datée du futur trahit une
     * horloge faussée, volontairement ou non, et ne doit pas ouvrir une fenêtre
     * de rejeu plus longue que prévu.</p>
     */
    public boolean horodatageAcceptable(LocalDateTime signedAt) {
        if (signedAt == null) {
            return false;
        }
        // Comparaison en UTC, et non dans le fuseau du serveur.
        //
        // L'horodatage signé est convenu en UTC — c'est écrit dans le contrat
        // avec l'application. Comparer avec l'heure locale de la machine ferait
        // dépendre l'acceptation du fuseau où le conteneur tourne : un serveur
        // réglé sur l'heure du Bénin accepterait ce qu'un conteneur en UTC
        // refuserait, et le défaut n'apparaîtrait qu'au premier déplacement.
        // Le cas s'est produit en essai, avec quatre heures d'écart.
        Duration ecart = Duration.between(signedAt, LocalDateTime.now(java.time.ZoneOffset.UTC)).abs();
        return ecart.compareTo(FENETRE) <= 0;
    }
}
