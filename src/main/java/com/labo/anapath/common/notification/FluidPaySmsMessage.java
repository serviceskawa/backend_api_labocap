package com.labo.anapath.common.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un message de la charge utile {@code POST /api/v1/sms} de FluidPay.
 *
 * <p>Champs et contraintes repris du contrat publié
 * ({@code docs/openapi.yaml}, chemin {@code /api/v1/sms}). Les six champs
 * obligatoires y sont tous portés par ce record ; {@link #sender} est le seul
 * facultatif retenu, les autres — nom du destinataire, métadonnées — n'ont pas
 * d'usage ici.</p>
 *
 * @param provider       opérateur d'acheminement ({@code ourvoice}, {@code lafricamobile})
 * @param recipientPhone destinataire au format international, sans {@code +}
 * @param message        texte du SMS, au plus 1600 caractères
 * @param referenceId    référence lisible de l'envoi, de 15 à 50 caractères
 * @param sourceId       clé d'idempotence (UUID) : FluidPay refuse un second
 *                       envoi portant la même, ce qui évite de facturer deux
 *                       fois un SMS rejoué
 * @param sourceType     étiquette d'origine, qui sert à retrouver les envois
 *                       d'une même fonctionnalité dans le tableau de bord
 * @param sender         expéditeur affiché, au plus 11 caractères ; {@code null}
 *                       pour laisser FluidPay appliquer celui du compte
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FluidPaySmsMessage(
        String provider,
        @JsonProperty("recipient_phone") String recipientPhone,
        String message,
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("source_id") String sourceId,
        @JsonProperty("source_type") String sourceType,
        String sender) {
}
