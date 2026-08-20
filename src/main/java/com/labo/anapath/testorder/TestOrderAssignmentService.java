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
}
