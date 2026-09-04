package com.labo.anapath.common.notification;

/**
 * Issue d'un envoi accepté par FluidPay.
 *
 * @param batchId   identifiant de lot chez l'éditeur, pour retrouver l'envoi
 *                  dans son tableau de bord ; {@code null} s'il n'a pas été rendu
 * @param duplicate vrai si FluidPay a reconnu un envoi déjà accepté pour la même
 *                  clé d'idempotence, et n'a donc rien renvoyé au destinataire
 */
public record FluidPaySmsResult(String batchId, boolean duplicate) {

    static FluidPaySmsResult accepte(String batchId) {
        return new FluidPaySmsResult(batchId, false);
    }

    static FluidPaySmsResult doublon() {
        return new FluidPaySmsResult(null, true);
    }
}
