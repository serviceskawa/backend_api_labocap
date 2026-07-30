package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface EmployeeTimeoffService {
    PageResponse<EmployeeTimeoffResponseDto> findAll(int page, int size, UUID employeeId);
    EmployeeTimeoffResponseDto findById(UUID id);
    EmployeeTimeoffResponseDto create(EmployeeTimeoffRequestDto dto, UUID employeeId);
    EmployeeTimeoffResponseDto update(UUID id, EmployeeTimeoffRequestDto dto, UUID employeeId);
    EmployeeTimeoffResponseDto updateStatus(UUID id, TimeoffStatusUpdateDto dto);
    void delete(UUID id);
}
