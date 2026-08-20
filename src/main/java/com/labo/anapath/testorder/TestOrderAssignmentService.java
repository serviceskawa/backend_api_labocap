package com.labo.anapath.testorder;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface TestOrderAssignmentService {

    AssignmentResponseDto create(AssignmentRequestDto dto, UUID branchId);

    PageResponse<AssignmentResponseDto> findAll(int page, int size, UUID branchId);

    PageResponse<AssignmentResponseDto> findAllImmuno(int page, int size, UUID branchId);

    AssignmentDetailResponseDto addDetail(UUID assignmentId, AssignmentDetailRequestDto dto);

    AssignmentPrintDto getPrintData(UUID assignmentId);

    AssignmentResponseDto update(UUID id, AssignmentRequestDto dto);

    void deleteDetail(UUID detailId);

    /**
     * Le vocabulaire d'étiquettes déjà employé par la branche.
     *
     * <p>Proposé à la saisie pour éviter qu'une même étiquette soit réécrite —
     * ou réécrite autrement — d'une affectation à l'autre.</p>
     */
    java.util.List<String> etiquettesConnues(java.util.UUID branchId);

    /**
     * Verse une étiquette au catalogue, sans attendre qu'une demande soit
     * affectée.
     *
     * <p>Une étiquette n'existait qu'au moment où l'ajout d'une demande
     * aboutissait : la saisir puis renoncer la perdait, et rien ne permettait
     * d'en déclarer une à l'avance. Le vocabulaire d'un laboratoire se pose
     * pourtant avant qu'on s'en serve.</p>
     *
     * @return le catalogue complet, étiquette comprise
     */
    java.util.List<String> ajouterAuCatalogue(java.util.UUID branchId, String valeur);
}
