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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeContratServiceTest {

    @Mock EmployeeContratRepository contratRepository;
    @Mock EmployeeRepository employeeRepository;

    EmployeeContratServiceImpl service;

    private final UUID EMP_ID     = UUID.randomUUID();
    private final UUID CONTRAT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new EmployeeContratServiceImpl(contratRepository, employeeRepository);
    }

    private Employee buildEmployee() {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", EMP_ID);
        return e;
    }

    private EmployeeContrat buildContrat(Employee emp) {
        EmployeeContrat c = new EmployeeContrat();
        ReflectionTestUtils.setField(c, "id", CONTRAT_ID);
        c.setEmployee(emp);
        c.setStartDate(LocalDate.of(2024, 1, 1));
        c.setSalary(new BigDecimal("350000"));
        c.setType("CDI");
        return c;
    }

    @Test
    @DisplayName("create - employé valide → contrat enregistré avec bon employeeId")
    void create_validEmployee_savesContrat() {
        Employee emp = buildEmployee();
        EmployeeContrat saved = buildContrat(emp);
        when(employeeRepository.findByIdAndBranchId(eq(EMP_ID), any())).thenReturn(Optional.of(emp));
        when(contratRepository.save(any())).thenReturn(saved);

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("350000"));
        dto.setType("CDI");

        EmployeeContratResponseDto result = service.create(dto, EMP_ID);

        ArgumentCaptor<EmployeeContrat> captor = ArgumentCaptor.forClass(EmployeeContrat.class);
        verify(contratRepository).save(captor.capture());
        assertThat(captor.getValue().getEmployee()).isEqualTo(emp);
        assertThat(result.employeeId()).isEqualTo(EMP_ID);
    }

    @Test
    @DisplayName("create - employé inconnu → ResourceNotFoundException")
    void create_unknownEmployee_throws() {
        when(employeeRepository.findByIdAndBranchId(eq(EMP_ID), any())).thenReturn(Optional.empty());

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("350000"));

        assertThatThrownBy(() -> service.create(dto, EMP_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update - contrat inconnu → ResourceNotFoundException")
    void update_unknownContrat_throws() {
        when(contratRepository.findById(CONTRAT_ID)).thenReturn(Optional.empty());

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("400000"));

        assertThatThrownBy(() -> service.update(CONTRAT_ID, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete - contrat inconnu → ResourceNotFoundException")
    void delete_unknownContrat_throws() {
        when(contratRepository.findById(CONTRAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(CONTRAT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
