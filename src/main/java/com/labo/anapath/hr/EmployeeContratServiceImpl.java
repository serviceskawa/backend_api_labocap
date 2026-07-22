package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeContratServiceImpl implements EmployeeContratService {

    private final EmployeeContratRepository employeeContratRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeContratResponseDto> findAll(int page, int size, UUID employeeId) {
        return PageResponse.of(employeeContratRepository.findByEmployeeId(employeeId,
                PageRequest.of(page, size)).map(this::toDto));
    }

    @Override
    @Transactional
    public EmployeeContratResponseDto create(EmployeeContratRequestDto dto, UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", employeeId));
        EmployeeContrat contrat = new EmployeeContrat();
        contrat.setEmployee(employee);
        apply(contrat, dto);
        return toDto(employeeContratRepository.save(contrat));
    }

    @Override
    @Transactional
    public EmployeeContratResponseDto update(UUID id, EmployeeContratRequestDto dto) {
        EmployeeContrat contrat = employeeContratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat employé", id));
        apply(contrat, dto);
        return toDto(employeeContratRepository.save(contrat));
    }

    /** Recopie tous les champs du DTO (onglets Contrat + Paie) sur l'entité. */
    private void apply(EmployeeContrat contrat, EmployeeContratRequestDto dto) {
        contrat.setType(dto.getType());
        contrat.setStartDate(dto.getStartDate());
        contrat.setEndDate(dto.getEndDate());
        contrat.setSalary(dto.getSalary());
        contrat.setProbationEndDate(dto.getProbationEndDate());
        contrat.setWeeklyWorkHours(dto.getWeeklyWorkHours());
        contrat.setWorkingDaysPerWeek(dto.getWorkingDaysPerWeek());
        contrat.setTerminationReason(dto.getTerminationReason());
        contrat.setHourlyGrossRate(dto.getHourlyGrossRate());
        contrat.setTransportAllowance(dto.getTransportAllowance());
        contrat.setIban(dto.getIban());
        contrat.setBic(dto.getBic());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        employeeContratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat employé", id));
        employeeContratRepository.deleteById(id);
    }

    private EmployeeContratResponseDto toDto(EmployeeContrat c) {
        return new EmployeeContratResponseDto(
                c.getId(), c.getEmployee().getId(), c.getType(),
                c.getStartDate(), c.getEndDate(), c.getSalary(),
                c.getProbationEndDate(), c.getWeeklyWorkHours(), c.getWorkingDaysPerWeek(),
                c.getTerminationReason(), c.getHourlyGrossRate(), c.getTransportAllowance(),
                c.getIban(), c.getBic(), c.getCreatedAt());
    }
}
