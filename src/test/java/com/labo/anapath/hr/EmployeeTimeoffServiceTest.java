package com.labo.anapath.hr;

import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeTimeoffServiceTest {

    @Mock EmployeeTimeoffRepository timeoffRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock UserRepository userRepository;
    @Mock EmailService emailService;
    @Mock NotificationSettings notificationSettings;

    EmployeeTimeoffServiceImpl service;

    private final UUID EMP_ID     = UUID.randomUUID();
    private final UUID TIMEOFF_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new EmployeeTimeoffServiceImpl(timeoffRepository, employeeRepository, userRepository,
                emailService, notificationSettings);
    }

    private Employee buildEmployee() {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", EMP_ID);
        return e;
    }

    private EmployeeTimeoff buildTimeoff(Employee emp, TimeoffStatus status) {
        EmployeeTimeoff t = new EmployeeTimeoff();
        ReflectionTestUtils.setField(t, "id", TIMEOFF_ID);
        t.setEmployee(emp);
        t.setStartDate(LocalDate.of(2026, 6, 1));
        t.setEndDate(LocalDate.of(2026, 6, 14));
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("create - statut initial toujours PENDING")
    void create_statusIsPending() {
        Employee emp = buildEmployee();
        EmployeeTimeoff saved = buildTimeoff(emp, TimeoffStatus.PENDING);
        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));
        when(timeoffRepository.save(any())).thenReturn(saved);

        EmployeeTimeoffRequestDto dto = new EmployeeTimeoffRequestDto();
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 6, 14));

        EmployeeTimeoffResponseDto result = service.create(dto, EMP_ID);

        assertThat(result.status()).isEqualTo(TimeoffStatus.PENDING);
    }

    @Test
    @DisplayName("create - employé inconnu → ResourceNotFoundException")
    void create_unknownEmployee_throws() {
        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.empty());

        EmployeeTimeoffRequestDto dto = new EmployeeTimeoffRequestDto();
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 6, 14));

        assertThatThrownBy(() -> service.create(dto, EMP_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateStatus → APPROVED : User.isActive mis à false")
    void updateStatus_toApproved_setsUserIsActiveFalse() {
        User user = new User();
        Employee emp = buildEmployee();
        emp.setUser(user);
        EmployeeTimeoff timeoff = buildTimeoff(emp, TimeoffStatus.PENDING);
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.of(timeoff));
        when(timeoffRepository.save(any())).thenReturn(timeoff);

        TimeoffStatusUpdateDto dto = new TimeoffStatusUpdateDto();
        dto.setStatus(TimeoffStatus.APPROVED);

        service.updateStatus(TIMEOFF_ID, dto);

        assertThat(user.isActive()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateStatus → APPROVED, employé sans User : pas de mise à jour User")
    void updateStatus_toApproved_noUser_noUserUpdate() {
        Employee emp = buildEmployee();
        EmployeeTimeoff timeoff = buildTimeoff(emp, TimeoffStatus.PENDING);
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.of(timeoff));
        when(timeoffRepository.save(any())).thenReturn(timeoff);

        TimeoffStatusUpdateDto dto = new TimeoffStatusUpdateDto();
        dto.setStatus(TimeoffStatus.APPROVED);

        service.updateStatus(TIMEOFF_ID, dto);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus APPROVED → REJECTED : User.isActive remis à true")
    void updateStatus_toRejected_fromApproved_setsUserIsActiveTrue() {
        User user = new User();
        user.setActive(false);
        Employee emp = buildEmployee();
        emp.setUser(user);
        EmployeeTimeoff timeoff = buildTimeoff(emp, TimeoffStatus.APPROVED);
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.of(timeoff));
        when(timeoffRepository.save(any())).thenReturn(timeoff);

        TimeoffStatusUpdateDto dto = new TimeoffStatusUpdateDto();
        dto.setStatus(TimeoffStatus.REJECTED);

        service.updateStatus(TIMEOFF_ID, dto);

        assertThat(user.isActive()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateStatus PENDING → REJECTED : pas de mise à jour User")
    void updateStatus_toRejected_fromPending_noUserUpdate() {
        User user = new User();
        Employee emp = buildEmployee();
        emp.setUser(user);
        EmployeeTimeoff timeoff = buildTimeoff(emp, TimeoffStatus.PENDING);
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.of(timeoff));
        when(timeoffRepository.save(any())).thenReturn(timeoff);

        TimeoffStatusUpdateDto dto = new TimeoffStatusUpdateDto();
        dto.setStatus(TimeoffStatus.REJECTED);

        service.updateStatus(TIMEOFF_ID, dto);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete - statut PENDING → suppression réussie")
    void delete_pendingStatus_deletes() {
        Employee emp = buildEmployee();
        EmployeeTimeoff timeoff = buildTimeoff(emp, TimeoffStatus.PENDING);
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.of(timeoff));

        service.delete(TIMEOFF_ID);

        verify(timeoffRepository).deleteById(TIMEOFF_ID);
    }

    @Test
    @DisplayName("delete - statut APPROVED → InvalidOperationException")
    void delete_approvedStatus_throws() {
        Employee emp = buildEmployee();
        EmployeeTimeoff timeoff = buildTimeoff(emp, TimeoffStatus.APPROVED);
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.of(timeoff));

        assertThatThrownBy(() -> service.delete(TIMEOFF_ID))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("delete - congé inconnu → ResourceNotFoundException")
    void delete_unknownId_throws() {
        when(timeoffRepository.findById(TIMEOFF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(TIMEOFF_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
