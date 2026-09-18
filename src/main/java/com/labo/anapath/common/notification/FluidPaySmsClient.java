package com.labo.anapath.common.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Client HTTP de la passerelle SMS FluidPay.
 *
 * <p>Seul point du code qui connaît le protocole de l'éditeur : en-têtes,
 * chemin, forme des réponses et des erreurs. Les appelants n'y voient qu'un
 * message à envoyer et un {@link FluidPaySmsResult} en retour.</p>
 *
 * <p><b>Le doublon n'est pas une erreur.</b> FluidPay déduplique sur
 * {@code source_id} et répond 422 quand un envoi a déjà été accepté pour cette
 * clé. C'est le résultat recherché — le destinataire a bien son SMS, et il n'en
 * reçoit pas un second — donc il est rendu comme un succès marqué
 * {@link FluidPaySmsResult#duplicate()}, et non levé.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FluidPaySmsClient {

    private final RestTemplate restTemplate;
    private final FluidPaySmsProperties properties;
    private final FluidPayPayloadCipher payloadCipher;
    private final ObjectMapper objectMapper;

    /**
     * Envoie un SMS.
     *
     * @param message message déjà constitué et validé par {@link SmsSender}
     * @return l'identifiant de lot rendu par FluidPay, ou la marque de doublon
     * @throws ExternalApiException si la passerelle n'est pas configurée, refuse
     *                              la requête ou est injoignable
     */
    public FluidPaySmsResult envoyer(FluidPaySmsMessage message) {
        if (!properties.isUsable()) {
            throw new ExternalApiException("FluidPay n'est pas configuré : renseignez "
                    + "app.fluidpay.sms.api-key et app.fluidpay.sms.encryption-key, "
                    + "et activez app.fluidpay.sms.enabled.");
        }

        String url = properties.urlEnvoi();
        // Le message ne circule jamais en clair : le serveur refuse une requête
        // non chiffrée, quoi qu'en dise le contrat publié.
        FluidPaySmsRequest corps = new FluidPaySmsRequest(payloadCipher.chiffrerMessages(
                properties.getEncryptionKey(), List.of(message)));
        HttpEntity<FluidPaySmsRequest> requete = new HttpEntity<>(corps, entetes());

        log.info("Envoi SMS FluidPay — provider={}, source_type={}, reference_id={}",
                message.provider(), message.sourceType(), message.referenceId());
        try {
            ResponseEntity<String> reponse =
                    restTemplate.exchange(url, HttpMethod.POST, requete, String.class);
            return lireSucces(reponse.getBody());

        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY && estDoublon(e)) {
                log.info("SMS {} déjà envoyé (source_id={}) : FluidPay a écarté le doublon",
                        message.referenceId(), message.sourceId());
                return FluidPaySmsResult.doublon();
            }
            throw new ExternalApiException(messageDErreur(e), e);

        } catch (RestClientException e) {
            log.error("FluidPay injoignable ({}): {}", url, e.getMessage());
            throw new ExternalApiException("La passerelle SMS FluidPay est injoignable.", e);
        }
    }

    /**
     * Extrait l'identifiant de lot d'une réponse acceptée.
     *
     * <p>Le corps est lu en {@code String} puis en arbre JSON, et non lié à un
     * record : {@code batch_id} sert à retrouver l'envoi dans le tableau de bord
     * de l'éditeur, mais son absence ne rend pas l'envoi moins abouti. Un contrat
     * strict ferait échouer un SMS pourtant parti.</p>
     */
    private FluidPaySmsResult lireSucces(String corps) {
        if (corps == null || corps.isBlank()) {
            log.warn("FluidPay a accepté l'envoi sans corps de réponse");
            return FluidPaySmsResult.accepte(null);
        }
        try {
            JsonNode racine = objectMapper.readTree(corps);
            // success:false avec un code 2xx : la passerelle a bien répondu, mais
            // n'a rien envoyé. Le taire ferait passer un échec pour un succès.
            if (racine.has("success") && !racine.path("success").asBoolean(true)) {
                throw new ExternalApiException("FluidPay a refusé l'envoi : "
                        + racine.path("message").asText("motif non précisé"));
            }
            return FluidPaySmsResult.accepte(
                    racine.path("data").path("batch_id").asText(null));
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Réponse FluidPay illisible, envoi considéré comme accepté : {}", e.getMessage());
            return FluidPaySmsResult.accepte(null);
        }
    }

    /**
     * Distingue le rejet pour doublon des autres refus en 422.
     *
     * <p>Le même code sert aux erreurs de validation — champ manquant, numéro
     * mal formé — qui, elles, doivent remonter. Seul le message documenté
     * (« All messages are duplicates of existing active records ») marque le
     * doublon.</p>
     */
    private boolean estDoublon(RestClientResponseException e) {
        String corps = e.getResponseBodyAsString();
        return corps != null && corps.toLowerCase().contains("duplicate");
    }

    private HttpHeaders entetes() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // Sans User-Agent explicite, Cloudflare répond une page de challenge HTML
        // en 403 au lieu du JSON attendu.
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
        return headers;
    }

    /** Traduit le corps d'erreur de l'éditeur en message lisible. */
    private String messageDErreur(RestClientResponseException e) {
        String corps = e.getResponseBodyAsString();
        try {
            String message = objectMapper.readTree(corps).path("message").asText(null);
            if (message != null && !message.isBlank()) {
                log.error("Erreur FluidPay SMS — {}", message);
                return "Envoi SMS refusé par FluidPay : " + message;
            }
        } catch (Exception ignore) {
            // Corps illisible : une page Cloudflare, par exemple. Le statut suffit.
        }
        log.error("Erreur FluidPay SMS HTTP {} — corps: {}", e.getStatusCode(),
                corps != null && corps.length() > 500 ? corps.substring(0, 500) + "…" : corps);
        return "La passerelle SMS FluidPay a refusé la requête (HTTP "
                + e.getStatusCode().value() + ").";
    }
}
