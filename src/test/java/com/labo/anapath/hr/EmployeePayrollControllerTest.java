package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeePayrollControllerTest {

    @Mock EmployeePayrollService employeePayrollService;
    @Mock PayrollPdfService payrollPdfService;

    EmployeePayrollController controller;

    private final UUID EMP_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        controller = new EmployeePayrollController(employeePayrollService, payrollPdfService);
    }

    private EmployeePayrollResponseDto dummyDto(BigDecimal gross, BigDecimal deductions) {
        return new EmployeePayrollResponseDto(UUID.randomUUID(), EMP_ID, 5, 2026,
                gross, deductions, gross.subtract(deductions), null, null);
    }

    @Test
    @DisplayName("create - retourne 201")
    void createPayroll_validRequest_returns201() {
        BigDecimal gross = new BigDecimal("350000");
        BigDecimal deductions = new BigDecimal("25000");
        when(employeePayrollService.create(any(), eq(EMP_ID))).thenReturn(dummyDto(gross, deductions));

        EmployeePayrollRequestDto dto = new EmployeePayrollRequestDto();
        dto.setMonth(5);
        dto.setYear(2026);
        dto.setGrossSalary(gross);
        dto.setDeductions(deductions);

        ResponseEntity<?> response = controller.create(EMP_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("create - employé inconnu → ResourceNotFoundException propagée")
    void createPayroll_unknownEmployee_throws() {
        when(employeePayrollService.create(any(), eq(EMP_ID)))
                .thenThrow(new ResourceNotFoundException("Employé", EMP_ID));

        EmployeePayrollRequestDto dto = new EmployeePayrollRequestDto();
        dto.setMonth(5);
        dto.setYear(2026);
        dto.setGrossSalary(new BigDecimal("300000"));

        assertThatThrownBy(() -> controller.create(EMP_ID, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAll - retourne page paginée")
    void findAllPayrolls_returnsPaginatedList() {
        when(employeePayrollService.findAll(0, 20, EMP_ID))
                .thenReturn(PageResponse.of(new PageImpl<>(List.of())));

        ResponseEntity<?> response = controller.findAll(EMP_ID, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
