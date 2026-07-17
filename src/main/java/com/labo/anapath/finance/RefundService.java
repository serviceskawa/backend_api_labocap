package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface RefundService {

    RefundRequestResponseDto create(RefundRequestCreateDto dto, UUID branchId, UUID userId);

    RefundRequestStatusResult updateStatus(UUID id, RefundRequestStatusUpdateDto dto, UUID userId);

    PageResponse<RefundRequestResponseDto> findAll(int page, int size, UUID branchId);

    RefundRequestResponseDto findById(UUID id);

    void delete(UUID id);

    long countPending(UUID branchId);
}
