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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeContratControllerTest {

    @Mock EmployeeContratService employeeContratService;

    EmployeeContratController controller;

    private final UUID EMP_ID     = UUID.randomUUID();
    private final UUID CONTRAT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        controller = new EmployeeContratController(employeeContratService);
    }

    private EmployeeContratResponseDto dummyDto() {
        // probationEndDate, weeklyWorkHours, workingDaysPerWeek, terminationReason,
        // hourlyGrossRate, transportAllowance, iban, bic, createdAt.
        return new EmployeeContratResponseDto(CONTRAT_ID, EMP_ID, "CDI",
                LocalDate.of(2024, 1, 1), null, new BigDecimal("350000"),
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("findAll - retourne page paginée")
    void findAll_returnsPaginatedList() {
        when(employeeContratService.findAll(0, 20, EMP_ID))
                .thenReturn(PageResponse.of(new PageImpl<>(List.of())));

        ResponseEntity<?> response = controller.findAll(EMP_ID, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("create - retourne 201")
    void createContrat_validRequest_returns201() {
        when(employeeContratService.create(any(), eq(EMP_ID))).thenReturn(dummyDto());

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("350000"));

        ResponseEntity<?> response = controller.create(EMP_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("create - employé inconnu → ResourceNotFoundException propagée")
    void createContrat_unknownEmployee_throws() {
        when(employeeContratService.create(any(), eq(EMP_ID)))
                .thenThrow(new ResourceNotFoundException("Employé", EMP_ID));

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("350000"));

        assertThatThrownBy(() -> controller.create(EMP_ID, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update - retourne 200")
    void updateContrat_validRequest_returns200() {
        when(employeeContratService.update(eq(CONTRAT_ID), any())).thenReturn(dummyDto());

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("400000"));

        ResponseEntity<?> response = controller.update(EMP_ID, CONTRAT_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("update - contrat inconnu → ResourceNotFoundException propagée")
    void updateContrat_unknownContrat_throws() {
        when(employeeContratService.update(eq(CONTRAT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Contrat employé", CONTRAT_ID));

        EmployeeContratRequestDto dto = new EmployeeContratRequestDto();
        dto.setStartDate(LocalDate.of(2024, 1, 1));
        dto.setSalary(new BigDecimal("350000"));

        assertThatThrownBy(() -> controller.update(EMP_ID, CONTRAT_ID, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete - retourne 200 et service.delete appelé")
    void deleteContrat_existingId_returns200() {
        ResponseEntity<?> response = controller.delete(EMP_ID, CONTRAT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(employeeContratService).delete(CONTRAT_ID);
    }

    @Test
    @DisplayName("delete - contrat inconnu → ResourceNotFoundException propagée")
    void deleteContrat_unknownId_throws() {
        doThrow(new ResourceNotFoundException("Contrat employé", CONTRAT_ID))
                .when(employeeContratService).delete(CONTRAT_ID);

        assertThatThrownBy(() -> controller.delete(EMP_ID, CONTRAT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
