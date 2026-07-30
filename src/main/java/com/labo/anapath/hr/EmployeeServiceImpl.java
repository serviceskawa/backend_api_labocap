package com.labo.anapath.hr;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.storage.FileStorageService;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Implémentation de {@link EmployeeService} gérant la logique métier
 * des employés du laboratoire.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmployeeMapper employeeMapper;
    private final FileStorageService fileStorageService;

    /**
     * {@inheritDoc}
     * Les employés sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(employeeRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(employeeMapper::toResponseDto));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public EmployeeResponseDto findById(UUID id) {
        return employeeMapper.toResponseDto(employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", id)));
    }

    /**
     * {@inheritDoc}
     * Le lien vers le compte utilisateur applicatif est optionnel : il est
     * établi uniquement si un {@code userId} est fourni dans le DTO.
     */
    @Override
    @Transactional
    public EmployeeResponseDto create(EmployeeRequestDto dto, UUID branchId) {
        Employee employee = employeeMapper.toEntity(dto);
        employee.setBranchId(branchId);
        // La colonne employees.salary est NOT NULL alors que le formulaire ne la
        // demande pas : le salaire réel vit dans les fiches de paie
        // (employee_payrolls.monthly_gross_salary), comme en Laravel. Sans ce
        // défaut, toute création échouait sur une violation de contrainte
        // remontée à l'utilisateur en « Une erreur interne est survenue ».
        if (employee.getSalary() == null) {
            employee.setSalary(java.math.BigDecimal.ZERO);
        }
        if (dto.getUserId() != null) {
            employee.setUser(userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getUserId())));
        }
        return employeeMapper.toResponseDto(employeeRepository.save(employee));
    }

    /**
     * {@inheritDoc}
     * Le salaire et la date d'embauche ne sont mis à jour que s'ils sont
     * renseignés dans le DTO (null = conserver la valeur existante).
     */
    @Override
    @Transactional
    public EmployeeResponseDto update(UUID id, EmployeeRequestDto dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", id));
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setPhone(dto.getPhone());
        employee.setEmail(dto.getEmail());
        employee.setPosition(dto.getPosition());
        if (dto.getSalary() != null) employee.setSalary(dto.getSalary());
        if (dto.getHireDate() != null) employee.setHireDate(dto.getHireDate());
        // Champs de profil : édités depuis la fiche détaillée. Un null conserve
        // l'existant, pour que l'édition rapide depuis la liste (qui ne renvoie
        // pas ces champs) n'écrase pas le profil.
        if (dto.getAddress() != null) employee.setAddress(dto.getAddress());
        if (dto.getDateOfBirth() != null) employee.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getPlaceOfBirth() != null) employee.setPlaceOfBirth(dto.getPlaceOfBirth());
        if (dto.getCnssNumber() != null) employee.setCnssNumber(dto.getCnssNumber());
        if (dto.getPhotoUrl() != null) employee.setPhotoUrl(dto.getPhotoUrl());
        if (dto.getGender() != null) employee.setGender(dto.getGender());
        if (dto.getNationality() != null) employee.setNationality(dto.getNationality());
        if (dto.getCity() != null) employee.setCity(dto.getCity());
        if (dto.getUserId() != null) {
            employee.setUser(userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getUserId())));
        }
        return employeeMapper.toResponseDto(employeeRepository.save(employee));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", id));
        employeeRepository.delete(employee);
    }

    /**
     * {@inheritDoc}
     * La photo est stockée sous {@code employees/photos} (validation d'extension
     * image assurée par {@link FileStorageService}). L'éventuelle photo
     * précédente est supprimée du disque.
     */
    @Override
    @Transactional
    public EmployeeResponseDto uploadPhoto(UUID id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidOperationException("Aucun fichier fourni");
        }
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé", id));
        String oldPhoto = employee.getPhotoUrl();
        String path = fileStorageService.store(file, "employees/photos");
        employee.setPhotoUrl(path);
        EmployeeResponseDto dto = employeeMapper.toResponseDto(employeeRepository.save(employee));
        if (oldPhoto != null && !oldPhoto.isBlank()) {
            try {
                fileStorageService.delete(oldPhoto);
            } catch (RuntimeException ex) {
                log.warn("Impossible de supprimer l'ancienne photo {} : {}", oldPhoto, ex.getMessage());
            }
        }
        return dto;
    }
}
