package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeTimeoffServiceImpl implements EmployeeTimeoffService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeeTimeoffRepository employeeTimeoffRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationSettings notificationSettings;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeTimeoffResponseDto> findAll(int page, int size, UUID employeeId) {
        return PageResponse.of(employeeTimeoffRepository.findByEmployeeId(employeeId,
                PageRequest.of(page, size)).map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeTimeoffResponseDto findById(UUID id) {
        return toDto(employeeTimeoffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Congé", id)));
    }

    @Override
    @Transactional
    public EmployeeTimeoffResponseDto create(EmployeeTimeoffRequestDto dto, UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", employeeId));
        EmployeeTimeoff timeoff = new EmployeeTimeoff();
        timeoff.setEmployee(employee);
        timeoff.setStartDate(dto.getStartDate());
        timeoff.setEndDate(dto.getEndDate());
        timeoff.setReason(dto.getReason());
        timeoff.setStatus(TimeoffStatus.PENDING);
        EmployeeTimeoff saved = employeeTimeoffRepository.save(timeoff);
        notifyAdminsOfRequest(saved, employee);
        return toDto(saved);
    }

    /**
     * Notifie les administrateurs ({@code admin_mails}) d'une nouvelle demande de congé.
     * Réplique Laravel : envoi conditionné à l'activation du service {@code conge}.
     */
    private void notifyAdminsOfRequest(EmployeeTimeoff timeoff, Employee employee) {
        UUID branchId = employee.getBranchId();
        if (!notificationSettings.serviceEnabled(branchId, "conge")) {
            return;
        }
        String labName = notificationSettings.labName(branchId);
        String employeeName = (employee.getFirstName() + " " + employee.getLastName()).trim();
        String start = timeoff.getStartDate() != null ? timeoff.getStartDate().format(DATE_FMT) : "";
        String end = timeoff.getEndDate() != null ? timeoff.getEndDate().format(DATE_FMT) : "";
        for (String to : notificationSettings.adminEmails(branchId)) {
            emailService.sendTimeoffRequestToAdmin(to, employeeName, start, end, labName);
        }
    }

    /**
     * Modifie une demande de congé — dates et motif.
     *
     * <p>Équivalent de {@code EmployeeTimeoffController::update()} en Laravel
     * (route {@code employee-timeoff-update}), qui manquait : seul le statut était
     * modifiable, on pouvait donc approuver ou refuser une demande mais pas en
     * corriger les dates.
     *
     * @param id         identifiant de la demande
     * @param dto        nouvelles dates et motif
     * @param employeeId employé propriétaire (contrôle de cohérence)
     * @return la demande mise à jour
     */
    @Override
    @Transactional
    public EmployeeTimeoffResponseDto update(UUID id, EmployeeTimeoffRequestDto dto, UUID employeeId) {
        EmployeeTimeoff timeoff = employeeTimeoffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Congé", id));
        // Sans ce contrôle, l'identifiant d'une demande suffirait à modifier le congé
        // d'un autre employé que celui de l'URL.
        if (!timeoff.getEmployee().getId().equals(employeeId)) {
            throw new ResourceNotFoundException("Congé", id);
        }
        timeoff.setStartDate(dto.getStartDate());
        timeoff.setEndDate(dto.getEndDate());
        timeoff.setReason(dto.getReason());
        return toDto(employeeTimeoffRepository.save(timeoff));
    }

    @Override
    @Transactional
    public EmployeeTimeoffResponseDto updateStatus(UUID id, TimeoffStatusUpdateDto dto) {
        EmployeeTimeoff timeoff = employeeTimeoffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Congé", id));

        TimeoffStatus previousStatus = timeoff.getStatus();
        timeoff.setStatus(dto.getStatus());
        EmployeeTimeoff saved = employeeTimeoffRepository.save(timeoff);

        Employee employee = timeoff.getEmployee();
        if (employee.getUser() != null) {
            User user = employee.getUser();
            if (dto.getStatus() == TimeoffStatus.APPROVED) {
                user.setActive(false);
                userRepository.save(user);
            } else if (previousStatus == TimeoffStatus.APPROVED
                    && (dto.getStatus() == TimeoffStatus.REJECTED
                        || dto.getStatus() == TimeoffStatus.PENDING)) {
                user.setActive(true);
                userRepository.save(user);
            }
        }

        // Réplique Laravel : l'employé n'est notifié qu'au passage en congé validé.
        if (previousStatus != TimeoffStatus.APPROVED && dto.getStatus() == TimeoffStatus.APPROVED) {
            notifyEmployeeOfApproval(timeoff, employee);
        }

        return toDto(saved);
    }

    /** Notifie l'employé que sa demande de congé a été validée. */
    private void notifyEmployeeOfApproval(EmployeeTimeoff timeoff, Employee employee) {
        String to = employee.getEmail();
        if ((to == null || to.isBlank()) && employee.getUser() != null) {
            to = employee.getUser().getEmail();
        }
        if (to == null || to.isBlank()) {
            return;
        }
        String labName = notificationSettings.labName(employee.getBranchId());
        String employeeName = (employee.getFirstName() + " " + employee.getLastName()).trim();
        String start = timeoff.getStartDate() != null ? timeoff.getStartDate().format(DATE_FMT) : "";
        String end = timeoff.getEndDate() != null ? timeoff.getEndDate().format(DATE_FMT) : "";
        emailService.sendTimeoffApprovedToEmployee(to, employeeName, start, end, labName);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        EmployeeTimeoff timeoff = employeeTimeoffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Congé", id));
        if (timeoff.getStatus() == TimeoffStatus.APPROVED) {
            throw new InvalidOperationException("Impossible de supprimer un congé approuvé");
        }
        employeeTimeoffRepository.deleteById(id);
    }

    private EmployeeTimeoffResponseDto toDto(EmployeeTimeoff t) {
        return new EmployeeTimeoffResponseDto(
                t.getId(), t.getEmployee().getId(), t.getStartDate(),
                t.getEndDate(), t.getReason(), t.getStatus(), t.getCreatedAt());
    }
}
