package com.labo.anapath.test;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de {@link LabTestService} gérant la logique métier des analyses du catalogue.
 *
 * <p>Responsabilités principales :
 * <ul>
 *   <li>Vérification de l'unicité du nom (insensible à la casse) dans la succursale</li>
 *   <li>Résolution des entités {@link CategoryTest} et {@link UnitMeasurement}
 *       à partir de leurs identifiants fournis dans le DTO</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabTestServiceImpl implements LabTestService {

    private final LabTestRepository labTestRepository;
    private final CategoryTestRepository categoryTestRepository;
    private final UnitMeasurementRepository unitMeasurementRepository;
    private final TestCatalogueMapper mapper;

    /**
     * {@inheritDoc}
     * Les résultats sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<LabTestResponseDto> findAll(int page, int size, String search, String status, UUID branchId) {
        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;
        String statusFilter = (status != null && !status.isBlank()) ? status.trim() : null;
        return PageResponse.of(
                // Sans tri Spring : `findByFilters` est une requête native, qui ne
                // traduit pas un tri exprimé sur les propriétés de l'entité
                // (« createdAt » ≠ colonne « created_at »). L'ordre est porté par
                // la requête elle-même.
                labTestRepository.findByFilters(branchId, searchFilter, statusFilter,
                                PageRequest.of(page, size))
                        .map(mapper::toLabTestResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestResponseDto> findAll(UUID branchId) {
        // Tri du plus récemment créé au plus ancien (formulaire d'ajout d'examen).
        return labTestRepository.findAllByBranchIdOrderByCreatedAtDesc(branchId)
                .stream().map(mapper::toLabTestResponseDto).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public LabTestResponseDto findById(UUID id) {
        return mapper.toLabTestResponseDto(labTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<LabTestResponseDto> search(String query, UUID branchId) {
        return labTestRepository.findByNameContainingIgnoreCaseAndBranchId(query, branchId)
                .stream().map(mapper::toLabTestResponseDto).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Si une catégorie ou une unité de mesure est spécifiée, l'entité correspondante
     * est chargée depuis la base de données et rattachée à l'analyse.</p>
     */
    @Override
    @Transactional
    public LabTestResponseDto create(LabTestRequestDto dto, UUID branchId) {
        if (labTestRepository.existsByNameIgnoreCaseAndBranchId(dto.getName(), branchId)) {
            throw new DuplicateResourceException("Une analyse '" + dto.getName() + "' existe déjà.");
        }
        LabTest entity = mapper.toLabTestEntity(dto);
        entity.setBranchId(branchId);
        if (dto.getCategoryTestId() != null) {
            entity.setCategoryTest(categoryTestRepository.findById(dto.getCategoryTestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie", dto.getCategoryTestId())));
        }
        if (dto.getUnitMeasurementId() != null) {
            entity.setUnitMeasurement(unitMeasurementRepository.findById(dto.getUnitMeasurementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unité", dto.getUnitMeasurementId())));
        }
        LabTest saved = labTestRepository.save(entity);
        log.info("Analyse créée: {}", saved.getId());
        return mapper.toLabTestResponseDto(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Les associations catégorie et unité de mesure sont re-résolues si les
     * identifiants fournis dans le DTO ont changé.</p>
     */
    @Override
    @Transactional
    public LabTestResponseDto update(UUID id, LabTestRequestDto dto) {
        LabTest entity = labTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", id));
        mapper.updateLabTestFromDto(dto, entity);
        if (dto.getCategoryTestId() != null) {
            entity.setCategoryTest(categoryTestRepository.findById(dto.getCategoryTestId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie", dto.getCategoryTestId())));
        }
        if (dto.getUnitMeasurementId() != null) {
            entity.setUnitMeasurement(unitMeasurementRepository.findById(dto.getUnitMeasurementId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unité", dto.getUnitMeasurementId())));
        }
        return mapper.toLabTestResponseDto(labTestRepository.save(entity));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        LabTest entity = labTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", id));
        labTestRepository.delete(entity);
    }
}
