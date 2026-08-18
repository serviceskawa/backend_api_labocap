package com.labo.anapath.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Client HTTP de la passerelle FluidInvoice.
 *
 * <p>Seul point du code qui connaît le protocole de l'éditeur : en-têtes,
 * chemins, forme des erreurs. Le service métier n'y voit que des DTO.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FluidInvoiceClient {

    private final RestTemplate restTemplate;
    private final FluidInvoiceProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Émet une facture de vente : création et normalisation en un seul appel.
     *
     * @param idempotencyKey clé stable propre à la facture — rejouer l'appel ne
     *                       doit jamais produire un second document fiscal.
     */
    public FluidInvoiceResponseDto emettre(FluidInvoiceRequestDto payload, String idempotencyKey) {
        return appeler("/invoices", payload, idempotencyKey);
    }

    /** Émet un avoir. Même corps, mais avec la référence obligatoire à l'originale. */
    public FluidInvoiceResponseDto emettreAvoir(FluidInvoiceRequestDto payload, String idempotencyKey) {
        return appeler("/invoices/credit-note", payload, idempotencyKey);
    }

    /**
     * Adresse du document normalisé chez l'éditeur.
     *
     * <p>Endpoint absent de la documentation, trouvé en sondant : il répond 422
     * et non 404. On la conserve telle quelle sur la facture, comme référence
     * d'audit — elle n'est pas ouvrable depuis un navigateur, qui n'enverrait
     * pas la clé API.</p>
     */
    public String urlDocument(String fluidInvoiceId) {
        return properties.getBaseUrl() + "/invoices/" + fluidInvoiceId + "/pdf";
    }

    /**
     * Télécharge le document normalisé.
     *
     * <p>Passe par le serveur parce que l'accès est authentifié par la clé API,
     * qui ne doit jamais atteindre le navigateur.</p>
     */
    public byte[] telechargerDocument(String fluidInvoiceId) {
        if (!properties.isUsable()) {
            throw new ExternalApiException(
                    "FluidInvoice n'est pas configuré : renseignez app.fluidinvoice.api-key et activez app.fluidinvoice.enabled.");
        }

        HttpHeaders headers = entetes(null);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON));

        String url = urlDocument(fluidInvoiceId);
        try {
            ResponseEntity<byte[]> reponse = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            byte[] corps = reponse.getBody();
            if (corps == null || corps.length == 0) {
                throw new ExternalApiException("FluidInvoice a renvoyé un document vide.");
            }
            return corps;

        } catch (RestClientResponseException e) {
            // Cas courant tant que le compte est incomplet : ISSUER_ADDRESS_MISSING,
            // « Adresse de l'émetteur manquante ». Le message de l'éditeur dit quoi
            // faire, on le remonte plutôt que de le masquer.
            throw new ExternalApiException(messageDErreur(e), e);
        } catch (RestClientException e) {
            log.error("FluidInvoice injoignable ({}): {}", url, e.getMessage());
            throw new ExternalApiException("FluidInvoice est injoignable.", e);
        }
    }

    private FluidInvoiceResponseDto appeler(String chemin, FluidInvoiceRequestDto payload, String idempotencyKey) {
        if (!properties.isUsable()) {
            throw new ExternalApiException(
                    "FluidInvoice n'est pas configuré : renseignez app.fluidinvoice.api-key et activez app.fluidinvoice.enabled.");
        }

        String url = properties.getBaseUrl() + chemin;
        HttpEntity<FluidInvoiceRequestDto> requete = new HttpEntity<>(payload, entetes(idempotencyKey));

        log.info("Appel FluidInvoice {} — idempotency-key={}", chemin, idempotencyKey);
        try {
            ResponseEntity<FluidInvoiceResponseDto> reponse =
                    restTemplate.exchange(url, HttpMethod.POST, requete, FluidInvoiceResponseDto.class);

            FluidInvoiceResponseDto corps = reponse.getBody();
            if (corps == null) {
                throw new ExternalApiException("FluidInvoice a répondu sans corps.");
            }
            // Le lien du document n'est pas documenté : on trace les clés reçues
            // pour l'identifier dès le premier appel réel, sans avoir à rejouer.
            log.info("Réponse FluidInvoice — id={}, status={}, champs non documentés={}",
                    corps.getId(), corps.getStatus(), corps.getExtra().keySet());
            return corps;

        } catch (RestClientResponseException e) {
            throw new ExternalApiException(messageDErreur(e), e);
        } catch (RestClientException e) {
            log.error("FluidInvoice injoignable ({}): {}", url, e.getMessage());
            throw new ExternalApiException("FluidInvoice est injoignable.", e);
        }
    }

    private HttpHeaders entetes(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // Sans User-Agent explicite, Cloudflare répond une page de challenge HTML
        // en 403 au lieu du JSON attendu.
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    /**
     * Traduit le corps d'erreur de l'éditeur en message lisible.
     *
     * <p>Forme documentée : {@code {"error": {"code": …, "message": …}}}. Le
     * message de l'éditeur est déjà rédigé en français et souvent actionnable
     * (« Contactez l'administrateur ») : on le remonte tel quel plutôt que de le
     * remplacer par une formule générique.</p>
     */
    private String messageDErreur(RestClientResponseException e) {
        String corps = e.getResponseBodyAsString();
        try {
            JsonNode erreur = objectMapper.readTree(corps).path("error");
            String message = erreur.path("message").asText(null);
            String code = erreur.path("code").asText(null);
            if (message != null && !message.isBlank()) {
                log.error("Erreur FluidInvoice {} — {}", code, message);
                return code == null ? message : message + " (" + code + ")";
            }
        } catch (Exception ignore) {
            // Corps illisible : une page Cloudflare, par exemple. Le brut suffit.
        }
        log.error("Erreur FluidInvoice HTTP {} — corps: {}", e.getStatusCode(),
                corps.length() > 500 ? corps.substring(0, 500) + "…" : corps);
        return "FluidInvoice a refusé la requête (HTTP " + e.getStatusCode().value() + ").";
    }
}
