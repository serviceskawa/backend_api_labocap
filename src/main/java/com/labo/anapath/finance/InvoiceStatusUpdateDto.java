package com.labo.anapath.finance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceStatusUpdateDto {

    @NotBlank
    private String payment;

    /**
     * Code de la facture normalisée (MECeF/DGI), saisi par le caissier.
     *
     * <p>Optionnel, comme dans Laravel {@code InvoiceController::updateStatus} qui ne le
     * valide pas côté serveur : il n'est écrit en {@code code_normalise} que lorsque la
     * normalisation automatique est désactivée ({@code settingInvoice.status != 1}).</p>
     */
    private String code;

    /**
     * À qui adresser la facture, si ce n'est pas ce qu'elle porte déjà.
     *
     * <p>Renseignée, elle remplace le nom, l'adresse et l'IFU et fige
     * l'identité : la revalidation du bon ne la réécrira plus depuis le
     * patient. Nulle, la facture garde ce qu'elle a.</p>
     */
    private IdentiteDeFacturation destinataire;
}
