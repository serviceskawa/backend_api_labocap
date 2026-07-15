package com.labo.anapath.contract;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface ContratService {

    PageResponse<ContratResponseDto> findAll(int page, int size, UUID branchId, String status, String search, String dateFrom, String dateTo);

    ContratResponseDto findById(UUID id);

    ContratResponseDto create(ContratRequestDto dto, UUID branchId);

    ContratResponseDto update(UUID id, ContratRequestDto dto);

    void delete(UUID id);

    DetailsContratDto addCategoryDetail(UUID contractId, CategoryDetailRequestDto dto);

    DetailsContratDto addTestDetail(UUID contractId, TestDetailRequestDto dto);

    DetailsContratDto updateTestDetail(UUID contractId, UUID detailId, TestDetailUpdateDto dto);

    ContratResponseDto activate(UUID contractId);

    ContratResponseDto close(UUID contractId);

    void deleteDetail(UUID contractId, UUID detailId);
}
