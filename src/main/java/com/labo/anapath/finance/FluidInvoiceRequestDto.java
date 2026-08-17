package com.labo.anapath.finance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Charge utile envoyée à {@code POST /v1/invoices} et
 * {@code POST /v1/invoices/credit-note}.
 *
 * <p>Les champs nuls sont omis : l'API distingue « absent » de « vide », et
 * {@code aib} notamment ne doit pas partir du tout — le laboratoire ne le prend
 * pas en compte.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FluidInvoiceRequestDto(

        /** FV (vente), FA (avoir), EV (export), EA (avoir export). */
        String type,

        List<Item> items,

        Client client,

        Operator operator,

        List<Payment> payment,

        /**
         * CodeMECeFDGI de la facture d'origine, 24 caractères. Obligatoire pour
         * un avoir (FA/EA), interdit ailleurs.
         */
        String reference,

        /** Alternative à {@link #reference} : l'UUID FluidInvoice de l'originale. */
        @JsonProperty("original_invoice_id")
        String originalInvoiceId
) {

    /**
     * Une ligne de facture.
     *
     * @param price prix unitaire TTC en FCFA, entier — <b>remise déjà déduite</b>,
     *              l'API n'ayant pas de champ remise.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String name, long price, BigDecimal quantity, String taxGroup) {}

    /** L'acheteur. Facultatif en entier : un patient n'a ni IFU ni raison sociale. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Client(String ifu, String name, String contact, String address) {}

    /** L'émetteur. {@code name} est le seul champ obligatoire de la charge utile. */
    public record Operator(String name) {}

    /** ESPECES, MOBILEMONEY, CARTEBANCAIRE, VIREMENT, CHEQUES, CREDIT, AUTRE. */
    public record Payment(String type, long amount) {}
}
