package com.labo.anapath.finance;

import java.util.UUID;

/**
 * Une facture vient d'être validée : elle est due au client, et téléchargeable.
 *
 * <p>Trois gestes la valident, et chacun publie cet événement :</p>
 * <ul>
 *   <li>l'encaissement en caisse ({@code PATCH /invoices/{id}/status}) — le geste
 *       quotidien, et de loin le plus fréquent ;</li>
 *   <li>la normalisation MECeF/DGI par machine e-MECeF
 *       ({@code POST /invoices/{id}/confirm-mecef}) ;</li>
 *   <li>la normalisation par FluidInvoice ({@code POST /invoices/{id}/normalize}).</li>
 * </ul>
 *
 * <p>Une même facture passe souvent par deux d'entre eux — encaissée, puis
 * normalisée. L'événement part donc plusieurs fois pour une seule facture, et
 * c'est {@link InvoiceSmsNotifier} qui garantit un unique SMS, en s'appuyant sur
 * {@link Invoice#getShareSmsSentAt()}. L'alternative — ne publier que depuis le
 * premier geste — supposerait de savoir lequel vient en premier, ce qui varie
 * selon que la normalisation automatique est activée pour la branche.</p>
 *
 * <p><b>Pourquoi un événement et non un appel direct.</b> L'encaissement engage
 * la caisse et la normalisation est un acte fiscal irréversible ; le SMS n'est
 * qu'une commodité. Les enchaîner dans la même transaction ferait annuler un
 * règlement pourtant encaissé parce qu'OurVoice était injoignable. L'écouteur ne
 * s'exécute qu'après commit.</p>
 *
 * @param invoiceId identifiant de la facture validée
 */
public record InvoiceValidatedEvent(UUID invoiceId) {
}
