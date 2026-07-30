package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeePayrollServiceImpl implements EmployeePayrollService {

    private final EmployeePayrollRepository employeePayrollRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeePayrollResponseDto> findAll(int page, int size, UUID employeeId) {
        return PageResponse.of(employeePayrollRepository.findByEmployeeId(employeeId,
                PageRequest.of(page, size)).map(this::toDto));
    }

    @Override
    @Transactional
    public EmployeePayrollResponseDto create(EmployeePayrollRequestDto dto, UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", employeeId));
        EmployeePayroll payroll = new EmployeePayroll();
        payroll.setEmployee(employee);
        payroll.setMonth(dto.getMonth());
        payroll.setYear(dto.getYear());
        payroll.setGrossSalary(dto.getGrossSalary());
        BigDecimal deductions = dto.getDeductions() != null ? dto.getDeductions() : BigDecimal.ZERO;
        payroll.setDeductions(deductions);
        payroll.setNetSalary(dto.getGrossSalary().subtract(deductions));
        payroll.setPaidAt(dto.getPaidAt());
        return toDto(employeePayrollRepository.save(payroll));
    }

    /**
     * Modifie une fiche de paie existante — équivalent de
     * {@code EmployeePayrollController::update()} en Laravel, absent jusqu'ici :
     * une fiche erronée était définitive.
     *
     * @param id         identifiant de la fiche
     * @param dto        nouvelles valeurs
     * @param employeeId employé propriétaire (contrôle de cohérence)
     * @return la fiche mise à jour
     */
    @Override
    @Transactional
    public EmployeePayrollResponseDto update(UUID id, EmployeePayrollRequestDto dto, UUID employeeId) {
        EmployeePayroll payroll = findOwned(id, employeeId);
        payroll.setMonth(dto.getMonth());
        payroll.setYear(dto.getYear());
        payroll.setGrossSalary(dto.getGrossSalary());
        BigDecimal deductions = dto.getDeductions() != null ? dto.getDeductions() : BigDecimal.ZERO;
        payroll.setDeductions(deductions);
        payroll.setNetSalary(dto.getGrossSalary().subtract(deductions));
        payroll.setPaidAt(dto.getPaidAt());
        return toDto(employeePayrollRepository.save(payroll));
    }

    /**
     * Supprime une fiche de paie — équivalent de {@code employee.payroll.delete}.
     *
     * @param id         identifiant de la fiche
     * @param employeeId employé propriétaire (contrôle de cohérence)
     */
    @Override
    @Transactional
    public void delete(UUID id, UUID employeeId) {
        employeePayrollRepository.delete(findOwned(id, employeeId));
    }

    /**
     * Charge une fiche de paie en vérifiant qu'elle appartient bien à l'employé de
     * l'URL : sans ce contrôle, l'identifiant d'une fiche suffirait à modifier la
     * paie d'un autre employé.
     */
    private EmployeePayroll findOwned(UUID id, UUID employeeId) {
        EmployeePayroll payroll = employeePayrollRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fiche de paie", id));
        if (!payroll.getEmployee().getId().equals(employeeId)) {
            throw new ResourceNotFoundException("Fiche de paie", id);
        }
        return payroll;
    }

    private EmployeePayrollResponseDto toDto(EmployeePayroll p) {
        return new EmployeePayrollResponseDto(
                p.getId(), p.getEmployee().getId(), p.getMonth(), p.getYear(),
                p.getGrossSalary(), p.getDeductions(), p.getNetSalary(),
                p.getPaidAt(), p.getCreatedAt());
    }
}
