package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ExpenseService {

    PageResponse<ExpenseResponseDto> findAll(int page, int size, UUID branchId, Integer paid, UUID expenseCategorieId);

    ExpenseResponseDto findById(UUID id);

    ExpenseResponseDto create(ExpenseCreateRequestDto dto, UUID branchId);

    ExpenseResponseDto update(UUID id, ExpenseRequestDto dto);

    ExpenseResponseDto uploadReceipt(UUID id, MultipartFile file);

    void delete(UUID id);

    ExpenseResponseDto addDetail(UUID expenseId, ExpenceDetailRequestDto dto, UUID branchId);

    void removeDetail(UUID expenseId, UUID detailId);

    ExpenseResponseDto payExpense(UUID expenseId, UUID branchId, UUID userId);

    ExpenseResponseDto updateStock(UUID expenseId, UUID branchId, UUID userId);
}
