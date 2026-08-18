package com.labo.anapath.finance;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Propriétés de la passerelle de facturation normalisée FluidInvoice, lues depuis
 * {@code application.yml} sous le préfixe {@code app.fluidinvoice}.
 *
 * <p>FluidInvoice encapsule les trois étapes e-MCF de la DGI (soumission,
 * vérification fiscale, confirmation) derrière un seul appel. Elle ne remplace
 * pas {@link MecefServiceImpl}, qui parle directement au SYGMEF : les deux
 * chemins coexistent, celui-ci étant piloté par {@link #enabled}.</p>
 *
 * <pre>{@code
 * app:
 *   fluidinvoice:
 *     enabled: true
 *     base-url: https://invoice.fluidpay.link/v1
 *     api-key: fni_test_xxxxxxxx
 *     operator-name: CENTRE ADECHINA ANATOMIE PATHOLOGIQUE
 *     tax-group: A
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "app.fluidinvoice")
@Getter
@Setter
public class FluidInvoiceProperties {

    /** Active l'appel à FluidInvoice. Faux par défaut : sans clé, rien ne doit partir. */
    private boolean enabled = false;

    /** Racine de l'API, sans barre oblique finale. */
    private String baseUrl = "https://invoice.fluidpay.link/v1";

    /** Clé API. Préfixe {@code fni_test_} en test, {@code fni_live_} en production. */
    private String apiKey;

    /**
     * Nom porté par {@code operator.name}, seul champ obligatoire identifiant
     * l'émetteur dans la charge utile. Le vendeur lui-même (raison sociale, IFU)
     * n'est pas transmis : il est porté par le compte associé à la clé API.
     */
    private String operatorName;

    /**
     * Groupe de taxation appliqué à toutes les lignes.
     *
     * <p>« A » = exonéré, ce que sont les actes d'anatomie pathologique. La
     * classification fiscale engage le contribuable (Art. 168 CGI Bénin) : elle
     * se règle ici, jamais en dur dans le code.</p>
     */
    private String taxGroup = "A";

    /**
     * En-tête {@code User-Agent} des appels.
     *
     * <p>Le domaine est derrière Cloudflare, qui sert un challenge HTML — et non
     * du JSON — aux clients sans {@code User-Agent} explicite. Sans cet en-tête,
     * la réponse est une page « Just a moment… » en 403 et l'erreur remonte sous
     * forme d'un échec de désérialisation incompréhensible.</p>
     */
    private String userAgent = "LabocapAPI/1.0";

    /** Vrai si la passerelle est utilisable : activée et pourvue d'une clé. */
    public boolean isUsable() {
        return enabled && StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
    }
}
