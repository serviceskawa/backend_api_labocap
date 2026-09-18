package com.labo.anapath.common.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chiffre la charge utile attendue par FluidPay sur ses endpoints protégés.
 *
 * <p><b>Le serveur fait foi, pas la documentation.</b> {@code docs/openapi.yaml}
 * décrit {@code POST /api/v1/sms} comme prenant {@code messages} en clair. Le
 * serveur, lui, refuse une telle requête ({@code missing_encrypted_data}) et
 * réclame le même {@code encrypted_data} que les endpoints de paiement. Le
 * format retenu ici a donc été établi contre l'API réelle
 * ({@code app-dev.fluidpay.link}), et non lu dans le contrat.</p>
 *
 * <p><b>Format, vérifié appel par appel :</b></p>
 * <ul>
 *   <li>clé : les 32 caractères de la clé de chiffrement pris <em>tels quels</em>
 *       en octets — et non décodés depuis l'hexadécimal, ce qui donnerait
 *       16 octets et une erreur {@code decryption_error} ;</li>
 *   <li>AES-256-CBC, remplissage PKCS7, vecteur d'initialisation tiré au sort ;</li>
 *   <li>transmis en {@code base64(IV ‖ chiffré)}, le vecteur en tête ;</li>
 *   <li>le clair porte {@code nonce} et {@code timestamp} en plus des données —
 *       ce qui permet à l'éditeur d'écarter un rejeu.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FluidPayPayloadCipher {

    /** Longueur du vecteur d'initialisation d'AES-CBC, en octets. */
    private static final int LONGUEUR_IV = 16;

    /** Longueur attendue de la clé : AES-256 travaille sur 32 octets. */
    public static final int LONGUEUR_CLE = 32;

    private static final SecureRandom ALEA = new SecureRandom();

    private final ObjectMapper objectMapper;

    /**
     * Enveloppe et chiffre des données à destination de FluidPay.
     *
     * @param cle     clé de chiffrement du compte, 32 caractères
     * @param donnees champs propres à l'endpoint ; {@code nonce} et
     *                {@code timestamp} y sont ajoutés
     * @return la valeur à placer dans {@code encrypted_data}
     * @throws ExternalApiException si la clé n'a pas la bonne longueur ou si le
     *                              chiffrement échoue
     */
    public String chiffrer(String cle, Map<String, Object> donnees) {
        byte[] octetsCle = (cle == null ? "" : cle).getBytes(StandardCharsets.UTF_8);
        if (octetsCle.length != LONGUEUR_CLE) {
            throw new ExternalApiException("Clé de chiffrement FluidPay invalide : "
                    + LONGUEUR_CLE + " caractères attendus, " + octetsCle.length + " reçus "
                    + "(app.fluidpay.sms.encryption-key).");
        }

        // L'ordre d'insertion est conservé : le clair reste lisible tel qu'il est
        // documenté quand on le déchiffre pour diagnostiquer un refus.
        Map<String, Object> clair = new LinkedHashMap<>();
        clair.put("nonce", UUID.randomUUID().toString());
        clair.put("timestamp", Instant.now().getEpochSecond());
        clair.putAll(donnees);

        try {
            byte[] iv = new byte[LONGUEUR_IV];
            ALEA.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(octetsCle, "AES"), new IvParameterSpec(iv));
            byte[] chiffre = cipher.doFinal(
                    objectMapper.writeValueAsBytes(clair));

            byte[] sortie = new byte[iv.length + chiffre.length];
            System.arraycopy(iv, 0, sortie, 0, iv.length);
            System.arraycopy(chiffre, 0, sortie, iv.length, chiffre.length);
            return Base64.getEncoder().encodeToString(sortie);

        } catch (JsonProcessingException e) {
            throw new ExternalApiException("Charge utile FluidPay non sérialisable.", e);
        } catch (Exception e) {
            throw new ExternalApiException("Chiffrement de la charge utile FluidPay impossible : "
                    + e.getMessage(), e);
        }
    }

    /** Enveloppe un envoi de SMS : {@code messages} va dans le clair chiffré. */
    public String chiffrerMessages(String cle, List<FluidPaySmsMessage> messages) {
        return chiffrer(cle, Map.of("messages", messages));
    }
}
