package com.labo.anapath.testorder;

import com.labo.anapath.common.Discipline;

import java.util.List;

/**
 * Le bordereau d'un lot, tel qu'on l'imprime.
 *
 * <p>Rien n'y est propre à l'anatomie pathologique : code, note et statut de
 * chaque demande, médecin, date. Seule la {@code discipline} dit à l'écran
 * comment intituler la personne à qui le lot est remis — « Docteur » en
 * anatomie pathologique, biologiste ou technicien en biologie.</p>
 *
 * @param discipline la discipline des demandes du lot ; nulle pour un lot
 *                   encore vide, qui n'en a pas
 */
public record AssignmentPrintDto(
        AssignmentResponseDto assignment,
        List<AssignmentDetailResponseDto> details,
        String branchName,
        String branchAddress,
        Discipline discipline
) {

    /** Le bordereau d'avant la biologie, sans discipline. */
    public AssignmentPrintDto(AssignmentResponseDto assignment,
                              List<AssignmentDetailResponseDto> details,
                              String branchName,
                              String branchAddress) {
        this(assignment, details, branchName, branchAddress, null);
    }
}
