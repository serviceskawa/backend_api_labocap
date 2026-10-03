package com.labo.anapath.hr;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeePayrollServiceTest {

    @Mock EmployeePayrollRepository payrollRepository;
    @Mock EmployeeRepository employeeRepository;

    EmployeePayrollServiceImpl service;

    private final UUID EMP_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new EmployeePayrollServiceImpl(payrollRepository, employeeRepository);
    }

    private Employee buildEmployee() {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", EMP_ID);
        return e;
    }

    private EmployeePayroll buildPayroll(Employee emp, BigDecimal gross, BigDecimal deductions) {
        EmployeePayroll p = new EmployeePayroll();
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setEmployee(emp);
        p.setMonth(5);
        p.setYear(2026);
        p.setGrossSalary(gross);
        p.setDeductions(deductions);
        p.setNetSalary(gross.subtract(deductions));
        return p;
    }

    @Test
    @DisplayName("create - salaire net calculé automatiquement (brut - déductions)")
    void create_netSalaryCalculated() {
        Employee emp = buildEmployee();
        BigDecimal gross = new BigDecimal("350000");
        BigDecimal deductions = new BigDecimal("25000");
        when(employeeRepository.findByIdAndBranchId(eq(EMP_ID), any())).thenReturn(Optional.of(emp));
        when(payrollRepository.save(any())).thenReturn(buildPayroll(emp, gross, deductions));

        EmployeePayrollRequestDto dto = new EmployeePayrollRequestDto();
        dto.setMonth(5);
        dto.setYear(2026);
        dto.setGrossSalary(gross);
        dto.setDeductions(deductions);

        service.create(dto, EMP_ID);

        ArgumentCaptor<EmployeePayroll> captor = ArgumentCaptor.forClass(EmployeePayroll.class);
        verify(payrollRepository).save(captor.capture());
        assertThat(captor.getValue().getNetSalary()).isEqualByComparingTo(new BigDecimal("325000"));
    }

    @Test
    @DisplayName("create - déductions null → traité comme ZERO, net = brut")
    void create_nullDeductions_usesZero() {
        Employee emp = buildEmployee();
        BigDecimal gross = new BigDecimal("300000");
        when(employeeRepository.findByIdAndBranchId(eq(EMP_ID), any())).thenReturn(Optional.of(emp));
        when(payrollRepository.save(any())).thenReturn(buildPayroll(emp, gross, BigDecimal.ZERO));

        EmployeePayrollRequestDto dto = new EmployeePayrollRequestDto();
        dto.setMonth(4);
        dto.setYear(2026);
        dto.setGrossSalary(gross);
        dto.setDeductions(null);

        service.create(dto, EMP_ID);

        ArgumentCaptor<EmployeePayroll> captor = ArgumentCaptor.forClass(EmployeePayroll.class);
        verify(payrollRepository).save(captor.capture());
        assertThat(captor.getValue().getDeductions()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getNetSalary()).isEqualByComparingTo(gross);
    }

    @Test
    @DisplayName("create - employé inconnu → ResourceNotFoundException")
    void create_unknownEmployee_throws() {
        when(employeeRepository.findByIdAndBranchId(eq(EMP_ID), any())).thenReturn(Optional.empty());

        EmployeePayrollRequestDto dto = new EmployeePayrollRequestDto();
        dto.setMonth(5);
        dto.setYear(2026);
        dto.setGrossSalary(new BigDecimal("300000"));

        assertThatThrownBy(() -> service.create(dto, EMP_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
