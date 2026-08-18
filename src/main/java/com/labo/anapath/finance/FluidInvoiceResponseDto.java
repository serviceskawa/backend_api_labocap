package com.labo.anapath.finance;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Réponse de FluidInvoice à une émission de facture.
 *
 * <p>Les champs inconnus ne sont pas ignorés mais recueillis dans
 * {@link #getExtra()} : la documentation fournie décrit un corps sans lien vers
 * le document, alors que le flow métier en dépend entièrement. Tant que le point
 * n'est pas tranché par l'éditeur, mieux vaut tout garder et chercher le lien
 * parmi ce qui arrive réellement, plutôt que le laisser tomber silencieusement.</p>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FluidInvoiceResponseDto {

    /** Identifiant interne FluidInvoice. */
    private String id;

    /** UID e-MCF, clé de réconciliation avec la DGI. */
    private String uid;

    /** pending (brouillon), confirmed, error, cancelled. */
    private String status;

    private String type;

    private Security security;

    @JsonProperty("integrity_hash")
    private String integrityHash;

    /** Tout champ non documenté, dont le lien vers le document. */
    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        extra.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return extra;
    }

    /** Bloc de sécurité e-MCF, présent seulement sur une facture confirmée. */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Security {
        private String dateTime;
        private String qrCode;
        private String codeMECeFDGI;
        private String counters;
        private String nim;
    }

    /**
     * Noms sous lesquels le lien du document pourrait arriver.
     *
     * <p>Aucun n'est confirmé : la documentation n'en mentionne aucun. On les
     * essaie dans cet ordre, du plus explicite au plus générique.</p>
     */
    private static final String[] CANDIDATS_LIEN = {
            "document_url", "documentUrl", "invoice_url", "invoiceUrl",
            "pdf_url", "pdfUrl", "public_url", "publicUrl", "url", "link"
    };

    /**
     * Le lien vers le document normalisé, ou null s'il n'est pas dans la réponse.
     *
     * <p>Seule une valeur ressemblant à une URL est retenue : un champ nommé
     * « url » qui contiendrait un identifiant ferait un bouton mort.</p>
     */
    public String documentUrl() {
        for (String candidat : CANDIDATS_LIEN) {
            Object valeur = extra.get(candidat);
            if (valeur instanceof String texte && texte.startsWith("http")) {
                return texte;
            }
        }
        return null;
    }

    /** Le code MECeF/DGI, ou null si la facture n'est pas confirmée. */
    public String codeMecef() {
        return security == null ? null : security.getCodeMECeFDGI();
    }
}
