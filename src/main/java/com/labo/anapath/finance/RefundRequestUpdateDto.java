package com.labo.anapath.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Requête d'édition d'une demande de remboursement (calque Laravel `refund.request.update`).
 *
 * <p>La pièce jointe ({@code attachment}) n'est pas portée par ce DTO : elle est
 * transmise en multipart et son chemin est renseigné côté contrôleur avant appel
 * du service. Un fichier absent conserve la pièce jointe existante.</p>
 */
@Getter
@Setter
public class RefundRequestUpdateDto {

    @NotNull(message = "La facture est obligatoire")
    private UUID invoiceId;

    @NotNull(message = "Le motif est obligatoire")
    private UUID refundReasonId;

    @NotNull(message = "Le montant est obligatoire")
    private BigDecimal montant;

    private String note;

    /** Chemin de la pièce jointe, renseigné par le contrôleur depuis le fichier uploadé. */
    private String attachment;
}
