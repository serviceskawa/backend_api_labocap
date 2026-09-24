package com.labo.anapath.test;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.module.ModulesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implémentation de {@link LabTestService} gérant la logique métier des analyses du catalogue.
 *
 * <p>Responsabilités principales :
 * <ul>
 *   <li>Vérification de l'unicité du nom (insensible à la casse) dans la succursale</li>
 *   <li>Résolution des entités {@link CategoryTest} et {@link UnitMeasurement}
 *       à partir de leurs identifiants fournis dans le DTO</li>
 *   <li>Règles de discipline : une analyse et sa catégorie sont de la même
 *       discipline ; la discipline et la nature biologique sont fixées à la création ;
 *       aucune analyse de biologie ne se crée tant que le module est désactivé</li>
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
    private final ModulesProperties modules;

    /**
     * {@inheritDoc}
     * Les résultats sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<LabTestResponseDto> findAll(int page, int size, String search, String status,
                                                    Discipline discipline, UUID branchId) {
        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : null;
        String statusFilter = (status != null && !status.isBlank()) ? status.trim() : null;
        return PageResponse.of(
                // Sans tri Spring : `findByFilters` est une requête native, qui ne
                // traduit pas un tri exprimé sur les propriétés de l'entité
                // (« createdAt » ≠ colonne « created_at »). L'ordre est porté par
                // la requête elle-même.
                labTestRepository.findByFilters(branchId, searchFilter, statusFilter,
                                disciplineOuDefaut(discipline).name(), PageRequest.of(page, size))
                        .map(mapper::toLabTestResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestResponseDto> findAll(UUID branchId, Discipline discipline) {
        // Tri du plus récemment créé au plus ancien (formulaire d'ajout d'examen).
        return labTestRepository.findAllByBranchIdAndDisciplineOrderByCreatedAtDesc(
                        branchId, disciplineOuDefaut(discipline))
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
    public List<LabTestResponseDto> search(String query, UUID branchId, Discipline discipline) {
        return labTestRepository.findByNameContainingIgnoreCaseAndBranchId(
                        query, branchId, disciplineOuDefaut(discipline).name())
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
        Discipline discipline = disciplineOuDefaut(dto.getDiscipline());
        if (discipline == Discipline.BIOLOGY && !modules.isBiology()) {
            throw new BusinessException("Le module Biologie n'est pas activé sur ce laboratoire.");
        }
        LabTest entity = mapper.toLabTestEntity(dto);
        entity.setBranchId(branchId);
        entity.setDiscipline(discipline);
        if (discipline == Discipline.BIOLOGY) {
            entity.setBiologyKind(dto.getBiologyKind() != null ? dto.getBiologyKind() : BiologyKind.PANEL);
        } else {
            refuserLesChampsDeBiologie(dto);
        }
        if (dto.getCategoryTestId() != null) {
            entity.setCategoryTest(categorieDeMemeDiscipline(dto.getCategoryTestId(), discipline));
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
        if (dto.getDiscipline() != null && dto.getDiscipline() != entity.getDiscipline()) {
            throw new BusinessException("La discipline d'une analyse ne se modifie pas après sa création.");
        }
        if (dto.getBiologyKind() != null && !Objects.equals(dto.getBiologyKind(), entity.getBiologyKind())) {
            throw new BusinessException("La nature d'une analyse de biologie ne se modifie pas après sa création.");
        }
        if (entity.getDiscipline() != Discipline.BIOLOGY) {
            refuserLesChampsDeBiologie(dto);
        }
        mapper.updateLabTestFromDto(dto, entity);
        if (dto.getCategoryTestId() != null) {
            entity.setCategoryTest(categorieDeMemeDiscipline(dto.getCategoryTestId(), entity.getDiscipline()));
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

    /** {@code null} (client qui n'envoie pas la discipline) vaut anatomie pathologique. */
    private static Discipline disciplineOuDefaut(Discipline discipline) {
        return discipline != null ? discipline : Discipline.PATHOLOGY;
    }

    /**
     * Charge la catégorie et vérifie qu'elle est de la discipline de l'analyse :
     * une analyse de biologie rangée dans une catégorie d'anatomie pathologique
     * apparaîtrait dans les écrans d'anapath, et fausserait les remises de contrat
     * qui visent la catégorie.
     */
    private CategoryTest categorieDeMemeDiscipline(UUID categoryTestId, Discipline discipline) {
        CategoryTest categorie = categoryTestRepository.findById(categoryTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", categoryTestId));
        if (categorie.getDiscipline() != discipline) {
            throw new BusinessException("La catégorie « " + categorie.getName()
                    + " » n'est pas de la même discipline que l'analyse.");
        }
        return categorie;
    }

    /** Nature biologique et type d'échantillon n'ont pas de sens en anatomie pathologique. */
    private static void refuserLesChampsDeBiologie(LabTestRequestDto dto) {
        if (dto.getBiologyKind() != null || (dto.getSpecimenType() != null && !dto.getSpecimenType().isBlank())) {
            throw new BusinessException(
                    "La nature biologique et le type d'échantillon sont réservés aux analyses de biologie.");
        }
    }
}
