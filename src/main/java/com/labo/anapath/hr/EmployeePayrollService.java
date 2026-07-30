package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface EmployeePayrollService {
    PageResponse<EmployeePayrollResponseDto> findAll(int page, int size, UUID employeeId);
    EmployeePayrollResponseDto create(EmployeePayrollRequestDto dto, UUID employeeId);
    EmployeePayrollResponseDto update(UUID id, EmployeePayrollRequestDto dto, UUID employeeId);
    void delete(UUID id, UUID employeeId);
}
