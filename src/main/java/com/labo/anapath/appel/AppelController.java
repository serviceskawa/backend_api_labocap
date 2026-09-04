package com.labo.anapath.appel;

import com.labo.anapath.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Ce dont un téléphone a besoin pour joindre l'autre.
 *
 * <h2>Pourquoi un relais est indispensable ici</h2>
 *
 * <p>Les abonnés mobiles béninois sont derrière un NAT d'opérateur : deux
 * téléphones ne peuvent pas s'adresser directement, quoi qu'en dise la théorie
 * du pair-à-pair. Sans relais, une partie des appels échoue — et de façon
 * apparemment aléatoire, ce qui est le pire des symptômes à diagnostiquer.</p>
 *
 * <h2>Des identifiants qui expirent</h2>
 *
 * <p>Le relais ne connaît pas d'utilisateurs : il vérifie une signature. Le
 * serveur délivre un couple valable quelques heures, calculé à partir d'un
 * secret partagé avec coturn. Un identifiant recopié depuis un téléphone cesse
 * donc de servir tout seul — là où un mot de passe fixe dans l'application
 * ouvrirait le relais à qui décompile l'APK.</p>
 */
@RestController
@RequestMapping("/api/v1/appels")
@RequiredArgsConstructor
public class AppelController {

    @Value("${app.appels.turn-url:}")
    private String turnUrl;

    @Value("${app.appels.turn-secret:}")
    private String turnSecret;

    @Value("${app.appels.turn-ttl-secondes:86400}")
    private long turnTtl;

    @Value("${app.appels.stun-url:stun:stun.l.google.com:19302}")
    private String stunUrl;

    /**
     * Les serveurs à essayer, dans l'ordre où WebRTC les essaiera.
     *
     * <p>STUN d'abord : quand la connexion directe passe, elle ne coûte rien à
     * personne et la voix ne traverse aucun tiers. TURN en second, pour les cas
     * — nombreux — où elle ne passe pas.</p>
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> config() {
        List<Map<String, Object>> serveurs = new java.util.ArrayList<>();
        serveurs.add(Map.of("urls", stunUrl));

        if (!turnUrl.isBlank() && !turnSecret.isBlank()) {
            long expiration = System.currentTimeMillis() / 1000 + turnTtl;
            String utilisateur = expiration + ":anapath";
            String motDePasse = signer(utilisateur, turnSecret);
            if (motDePasse != null) {
                serveurs.add(Map.of(
                        "urls", turnUrl,
                        "username", utilisateur,
                        "credential", motDePasse));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "iceServers", serveurs,
                // Dit à l'application ce que le serveur sait faire : sans relais
                // configuré, elle prévient au lieu de laisser l'appel échouer
                // sans explication.
                "relaisConfigure", !turnUrl.isBlank() && !turnSecret.isBlank(),
                "maximumParticipants", Appel.MAXIMUM)));
    }

    /**
     * HMAC-SHA1 en base64 : la convention {@code use-auth-secret} de coturn.
     *
     * <p>Visible pour le test, qui compare le résultat à un vecteur éprouvé
     * contre un vrai coturn. Les trois façons de se tromper ici — SHA-256 au
     * lieu de SHA-1, hexadécimal au lieu de base64, nom d'utilisateur sans
     * l'horodatage — donnent toutes le même symptôme : un 401 muet, au moment
     * précis où l'appel devait s'établir.</p>
     */
    static String signer(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }
}
