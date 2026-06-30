package com.labo.anapath.test;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation de {@link CategoryTestService} gérant la logique métier
 * des catégories d'analyses.
 *
 * <p>Responsabilités principales :
 * <ul>
 *   <li>Vérification de l'unicité du nom (insensible à la casse) dans la succursale</li>
 *   <li>Protection contre la suppression d'une catégorie référencée par des analyses</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryTestServiceImpl implements CategoryTestService {

    private final CategoryTestRepository categoryTestRepository;
    /** Utilisé pour vérifier les dépendances avant suppression d'une catégorie. */
    private final LabTestRepository labTestRepository;
    private final TestCatalogueMapper mapper;

    /**
     * {@inheritDoc}
     * Les résultats sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryTestResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(
                categoryTestRepository.findByBranchId(branchId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                        .map(mapper::toCategoryTestResponseDto));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public CategoryTestResponseDto findById(UUID id) {
        return mapper.toCategoryTestResponseDto(
                categoryTestRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CategoryTestResponseDto create(CategoryTestRequestDto dto, UUID branchId) {
        if (categoryTestRepository.existsByCodeIgnoreCaseAndBranchId(dto.getCode(), branchId)) {
            throw new DuplicateResourceException("Le code '" + dto.getCode() + "' est déjà utilisé par une autre catégorie.");
        }
        if (categoryTestRepository.existsByNameIgnoreCaseAndBranchId(dto.getName(), branchId)) {
            throw new DuplicateResourceException("Une catégorie '" + dto.getName() + "' existe déjà.");
        }
        CategoryTest entity = mapper.toCategoryTestEntity(dto);
        entity.setBranchId(branchId);
        CategoryTest saved = categoryTestRepository.save(entity);
        log.info("Catégorie créée: {}", saved.getId());
        return mapper.toCategoryTestResponseDto(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CategoryTestResponseDto update(UUID id, CategoryTestRequestDto dto) {
        CategoryTest entity = categoryTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
        if (categoryTestRepository.existsByCodeIgnoreCaseAndBranchIdAndIdNot(dto.getCode(), entity.getBranchId(), id)) {
            throw new DuplicateResourceException("Le code '" + dto.getCode() + "' est déjà utilisé par une autre catégorie.");
        }
        if (categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndIdNot(dto.getName(), entity.getBranchId(), id)) {
            throw new DuplicateResourceException("Une catégorie '" + dto.getName() + "' existe déjà.");
        }
        mapper.updateCategoryTestFromDto(dto, entity);
        return mapper.toCategoryTestResponseDto(categoryTestRepository.save(entity));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Avant suppression, vérifie qu'aucune analyse ({@link LabTest}) ne référence
     * cette catégorie, afin de préserver l'intégrité référentielle du catalogue.</p>
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        CategoryTest entity = categoryTestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
        // Empêcher la suppression si des analyses sont rattachées à cette catégorie
        if (labTestRepository.existsByCategoryTest(entity)) {
            throw new BusinessException("Cette catégorie est référencée par des analyses.");
        }
        categoryTestRepository.delete(entity);
    }
}
