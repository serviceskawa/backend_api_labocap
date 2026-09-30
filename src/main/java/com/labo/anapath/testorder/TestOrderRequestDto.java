package com.labo.anapath.testorder;

import com.labo.anapath.common.Discipline;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO de requête pour la création ou la mise à jour d'un bon d'examen.
 *
 * <p>Seuls {@code patientId} et {@code prelevementDate} sont obligatoires.
 * Les autres champs sont optionnels et peuvent être renseignés progressivement.
 * La liste {@code details} contient les analyses demandées avec leurs remises éventuelles.
 */
@Getter
@Setter
public class TestOrderRequestDto {

    /** Identifiant du patient concerné par l'examen (obligatoire). */
    @NotNull(message = "Le patient est obligatoire")
    private UUID patientId;

    /** Date à laquelle le prélèvement a été réalisé (obligatoire). */
    @NotNull(message = "La date de prélèvement est obligatoire")
    private LocalDate prelevementDate;

    /**
     * Type de bon d'examen (biopsie, cytologie, etc.).
     *
     * <p>Propre à l'anatomie pathologique : un bon de biologie n'en porte pas
     * (les compteurs par type — immuno, cyto/histo — ne filtrent pas la discipline).
     */
    private UUID typeOrderId;

    /**
     * Discipline du bon ; {@code null} vaut {@link Discipline#PATHOLOGY}, pour que
     * les clients qui ne l'envoient pas (web actuel, mobile) créent ce qu'ils
     * créaient. Fixée à la création : en modification, seule la valeur du bon
     * (ou {@code null}) est acceptée.
     */
    private Discipline discipline;

    /** Identifiant du médecin prescripteur. */
    private UUID doctorId;

    /** Identifiant de l'hôpital d'origine du prélèvement. */
    private UUID hospitalId;

    /**
     * Identifiant du contrat de facturation.
     * Obligatoire lors de la validation (statut VALIDATED).
     */
    private UUID contratId;

    /** Référence interne de l'hôpital prescripteur. */
    private String referenceHopital;

    /** Indique si le bon doit être traité en priorité. */
    private Boolean isUrgent;

    /** Option tarifaire spécifique au contrat. */
    private Boolean option;

    /** Analyses complémentaires affiliées (texte libre). */
    private String testAffiliate;

    /** Sous-total du bon avant remise globale (nullable, calculé côté client ou à la validation). */
    @PositiveOrZero(message = "Le sous-total ne peut pas être négatif")
    private Double subtotal;

    /** Remise globale appliquée sur le bon (nullable). */
    @PositiveOrZero(message = "La remise ne peut pas être négative")
    private Double discount;

    /** Total final du bon après remise globale (nullable). */
    @PositiveOrZero(message = "Le total ne peut pas être négatif")
    private Double total;

    /** Identifiant du technicien/utilisateur assigné pour le traitement du bon. */
    private UUID assignedToUserId;

    /** Liste des analyses demandées avec leurs éventuelles remises individuelles. */
    @Valid
    private List<DetailTestOrderRequestDto> details = new ArrayList<>();
}
