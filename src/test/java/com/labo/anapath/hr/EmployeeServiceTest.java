package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeMapper employeeMapper;

    @InjectMocks private EmployeeServiceImpl service;

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID EMP_ID    = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();

    private Employee buildEmployee() {
        Employee e = new Employee();
        e.setBranchId(BRANCH_ID);
        e.setFirstName("Jean");
        e.setLastName("Dupont");
        e.setSalary(new BigDecimal("150000"));
        e.setHireDate(LocalDate.of(2022, 1, 1));
        return e;
    }

    private EmployeeResponseDto buildResponse(Employee e) {
        // address, dateOfBirth, placeOfBirth, cnssNumber, photoUrl, gender,
        // nationality, city, userId — puis branchId et createdAt.
        return new EmployeeResponseDto(EMP_ID, e.getFirstName(), e.getLastName(),
                e.getPhone(), e.getEmail(), e.getPosition(), e.getSalary(),
                e.getHireDate(), null, null, null, null, null, null, null, null, null,
                BRANCH_ID, null);
    }

    @Test
    @DisplayName("findAll - retourne page paginée")
    void findAll_returnsPaginatedPage() {
        Employee emp = buildEmployee();
        Page<Employee> page = new PageImpl<>(List.of(emp));
        when(employeeRepository.findByBranchId(eq(BRANCH_ID), any(Pageable.class))).thenReturn(page);
        when(employeeMapper.toResponseDto(emp)).thenReturn(buildResponse(emp));

        PageResponse<EmployeeResponseDto> result = service.findAll(0, 20, BRANCH_ID);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).firstName()).isEqualTo("Jean");
    }

    @Test
    @DisplayName("findById - ID existant → retourne DTO")
    void findById_existingId_returnsDto() {
        Employee emp = buildEmployee();
        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));
        when(employeeMapper.toResponseDto(emp)).thenReturn(buildResponse(emp));

        EmployeeResponseDto result = service.findById(EMP_ID);

        assertThat(result.lastName()).isEqualTo("Dupont");
    }

    @Test
    @DisplayName("findById - ID inconnu → ResourceNotFoundException")
    void findById_unknownId_throwsResourceNotFoundException() {
        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(EMP_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create - avec userId → résout le User et le lie à l'employé")
    void create_withUserId_linksUser() {
        EmployeeRequestDto dto = new EmployeeRequestDto();
        dto.setFirstName("Marie");
        dto.setLastName("Curie");
        dto.setUserId(USER_ID);

        Employee emp = buildEmployee();
        User user = new User();

        when(employeeMapper.toEntity(dto)).thenReturn(emp);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(employeeRepository.save(emp)).thenReturn(emp);
        when(employeeMapper.toResponseDto(emp)).thenReturn(buildResponse(emp));

        service.create(dto, BRANCH_ID);

        assertThat(emp.getUser()).isSameAs(user);
        verify(employeeRepository).save(emp);
    }

    @Test
    @DisplayName("create - sans userId → aucun User lié")
    void create_withoutUserId_noUserLink() {
        EmployeeRequestDto dto = new EmployeeRequestDto();
        dto.setFirstName("Paul");
        dto.setLastName("Martin");

        Employee emp = buildEmployee();

        when(employeeMapper.toEntity(dto)).thenReturn(emp);
        when(employeeRepository.save(emp)).thenReturn(emp);
        when(employeeMapper.toResponseDto(emp)).thenReturn(buildResponse(emp));

        service.create(dto, BRANCH_ID);

        assertThat(emp.getUser()).isNull();
    }

    @Test
    @DisplayName("update - salary et hireDate null → valeurs existantes préservées")
    void update_nullSalaryAndHireDate_preservesExisting() {
        Employee emp = buildEmployee();
        BigDecimal originalSalary = emp.getSalary();
        LocalDate originalHireDate = emp.getHireDate();

        EmployeeRequestDto dto = new EmployeeRequestDto();
        dto.setFirstName("Jean-Updated");
        dto.setLastName("Dupont");

        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));
        when(employeeRepository.save(emp)).thenReturn(emp);
        when(employeeMapper.toResponseDto(emp)).thenReturn(buildResponse(emp));

        service.update(EMP_ID, dto);

        assertThat(emp.getSalary()).isEqualByComparingTo(originalSalary);
        assertThat(emp.getHireDate()).isEqualTo(originalHireDate);
        assertThat(emp.getFirstName()).isEqualTo("Jean-Updated");
    }

    @Test
    @DisplayName("delete - ID existant → soft delete via repository")
    void delete_existingId_callsRepository() {
        Employee emp = buildEmployee();
        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));

        service.delete(EMP_ID);

        verify(employeeRepository).delete(emp);
    }

    @Test
    @DisplayName("create - userId inconnu → ResourceNotFoundException")
    void create_unknownUserId_throwsResourceNotFoundException() {
        EmployeeRequestDto dto = new EmployeeRequestDto();
        dto.setFirstName("X");
        dto.setLastName("Y");
        dto.setUserId(USER_ID);

        Employee emp = buildEmployee();
        when(employeeMapper.toEntity(dto)).thenReturn(emp);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
