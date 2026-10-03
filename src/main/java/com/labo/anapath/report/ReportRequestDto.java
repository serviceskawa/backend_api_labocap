package com.labo.anapath.report;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ReportRequestDto {

    /** Si fourni → mise à jour du rapport existant ; sinon → création. */
    private UUID reportId;

    /** Obligatoire à la création pour lier le CR à son bon d'examen. */
    private UUID testOrderId;

    private UUID titleId;
    @Size(max = 50000)
    private String content;
    @Size(max = 50000)
    private String contentMicro;
    @Size(max = 50000)
    private String comment;
    @Size(max = 50000)
    private String commentSup;
    @Size(max = 50000)
    private String descriptionSupplementaire;
    @Size(max = 50000)
    private String descriptionSupplementaireMicro;
    private UUID reviewedById;
    private UUID signatory1Id;
    private UUID signatory2Id;
    private UUID signatory3Id;
    @Size(max = 200)
    private String receiverName;

    /** "VALIDATED" pour valider ; "DRAFT" (ou null) pour brouillon. */
    private String status;

    private List<UUID> tagIds = new ArrayList<>();

    /**
     * Pourquoi on retouche un compte-rendu déjà sorti. Exigé (20 caractères
     * au moins) pour modifier un compte-rendu livré, journalisé et joint à
     * l'alerte envoyée aux administrateurs ; facultatif sinon.
     */
    @Size(max = 2000)
    private String reason;
}
