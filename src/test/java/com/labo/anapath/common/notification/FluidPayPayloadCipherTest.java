package com.labo.anapath.common.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.common.exception.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie le format établi contre l'API réelle : AES-256-CBC, clé prise telle
 * quelle en octets, {@code base64(IV ‖ chiffré)}, {@code nonce} et
 * {@code timestamp} dans le clair.
 */
class FluidPayPayloadCipherTest {

    private static final String CLE = "0123456789abcdef0123456789abcdef"; // 32 caractères
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FluidPayPayloadCipher cipher = new FluidPayPayloadCipher(objectMapper);

    /** Déchiffre comme le ferait FluidPay, pour vérifier ce qui est réellement transmis. */
    private JsonNode dechiffrer(String encode) throws Exception {
        byte[] brut = Base64.getDecoder().decode(encode);
        byte[] iv = Arrays.copyOfRange(brut, 0, 16);
        byte[] chiffre = Arrays.copyOfRange(brut, 16, brut.length);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(CLE.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(iv));
        return objectMapper.readTree(c.doFinal(chiffre));
    }

    @Test
    @DisplayName("le clair porte nonce, timestamp et les données, et se relit après déchiffrement")
    void clairRelisibleApresDechiffrement() throws Exception {
        String encode = cipher.chiffrer(CLE, Map.of("messages", "peu importe"));

        JsonNode clair = dechiffrer(encode);

        assertThat(clair.path("nonce").asText()).isNotBlank();
        assertThat(clair.path("timestamp").asLong()).isPositive();
        assertThat(clair.path("messages").asText()).isEqualTo("peu importe");
    }

    @Test
    @DisplayName("les messages du SMS voyagent dans le clair chiffré")
    void messagesDansLeClairChiffre() throws Exception {
        FluidPaySmsMessage message = new FluidPaySmsMessage("ourvoice", "22997000001",
                "Votre facture est disponible", "labocap_invoice-0123456789abcdef",
                "aaa-bbb", SmsSender.SOURCE_FACTURE, null);

        JsonNode clair = dechiffrer(cipher.chiffrerMessages(CLE, java.util.List.of(message)));

        assertThat(clair.path("messages").get(0).path("recipient_phone").asText())
                .isEqualTo("22997000001");
        assertThat(clair.path("messages").get(0).path("provider").asText()).isEqualTo("ourvoice");
    }

    @Test
    @DisplayName("le vecteur d'initialisation change à chaque envoi")
    void vecteurDInitialisationDifferentAChaqueFois() {
        String a = cipher.chiffrer(CLE, Map.of("x", 1));
        String b = cipher.chiffrer(CLE, Map.of("x", 1));

        // Même contenu, chiffrés différemment : sans cela, deux envois identiques
        // seraient reconnaissables sur le réseau.
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("clé de mauvaise longueur → message nommant le réglage à corriger")
    void cleDeMauvaiseLongueur_messageExplicite() {
        assertThatThrownBy(() -> cipher.chiffrer("trop-courte", Map.of("x", 1)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("32 caractères attendus")
                .hasMessageContaining("encryption-key");
    }

    @Test
    @DisplayName("clé absente → même refus explicite, pas de NullPointerException")
    void cleAbsente_refusExplicite() {
        assertThatThrownBy(() -> cipher.chiffrer(null, Map.of("x", 1)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("32 caractères attendus");
    }
}
