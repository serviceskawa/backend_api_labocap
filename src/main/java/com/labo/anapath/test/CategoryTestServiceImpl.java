package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.module.ModulesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de {@link CategoryTestService} gérant la logique métier
 * des catégories d'analyses.
 *
 * <p>Responsabilités principales :
 * <ul>
 *   <li>Vérification de l'unicité du nom (insensible à la casse) dans la discipline de la succursale</li>
 *   <li>Discipline fixée à la création ; pas de catégorie de biologie sans le module</li>
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
    private final ModulesProperties modules;

    /** Catégories proposées par {@link #createBiologyDefaults(UUID)}, dans l'ordre de création. */
    static final List<String> CATEGORIES_DE_BIOLOGIE_PAR_DEFAUT = List.of(
            "Hématologie", "Biochimie", "Immunologie/Sérologie", "Bactériologie", "Parasitologie");

    /**
     * {@inheritDoc}
     * Les résultats sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryTestResponseDto> findAll(int page, int size, Discipline discipline, UUID branchId) {
        return PageResponse.of(
                categoryTestRepository.findByBranchIdAndDiscipline(branchId, disciplineOuDefaut(discipline),
                                PageRequest.of(page, size, Sort.by("createdAt").descending()))
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
        Discipline discipline = disciplineOuDefaut(dto.getDiscipline());
        if (discipline == Discipline.BIOLOGY) {
            exigerLeModuleBiologie();
        }
        if (categoryTestRepository.existsByCodeIgnoreCaseAndBranchId(dto.getCode(), branchId)) {
            throw new DuplicateResourceException("Le code '" + dto.getCode() + "' est déjà utilisé par une autre catégorie.");
        }
        if (categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline(dto.getName(), branchId, discipline)) {
            throw new DuplicateResourceException("Une catégorie '" + dto.getName() + "' existe déjà.");
        }
        CategoryTest entity = mapper.toCategoryTestEntity(dto);
        entity.setBranchId(branchId);
        entity.setDiscipline(discipline);
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
        if (dto.getDiscipline() != null && dto.getDiscipline() != entity.getDiscipline()) {
            throw new BusinessException("La discipline d'une catégorie ne se modifie pas après sa création.");
        }
        if (categoryTestRepository.existsByCodeIgnoreCaseAndBranchIdAndIdNot(dto.getCode(), entity.getBranchId(), id)) {
            throw new DuplicateResourceException("Le code '" + dto.getCode() + "' est déjà utilisé par une autre catégorie.");
        }
        if (categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDisciplineAndIdNot(
                dto.getName(), entity.getBranchId(), entity.getDiscipline(), id)) {
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

    /**
     * {@inheritDoc}
     *
     * <p>La présence d'une catégorie est testée sur le nom, sans tenir compte de la
     * casse, parmi les catégories de biologie : une catégorie d'anatomie pathologique
     * homonyme n'empêche pas la création. Aucun code n'est attribué — le code est
     * unique dans toute la succursale et le laboratoire le choisit lui-même.</p>
     */
    @Override
    @Transactional
    public List<CategoryTestResponseDto> createBiologyDefaults(UUID branchId) {
        exigerLeModuleBiologie();
        int crees = 0;
        for (String nom : CATEGORIES_DE_BIOLOGIE_PAR_DEFAUT) {
            if (categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline(nom, branchId, Discipline.BIOLOGY)) {
                continue;
            }
            CategoryTest categorie = new CategoryTest();
            categorie.setName(nom);
            categorie.setBranchId(branchId);
            categorie.setDiscipline(Discipline.BIOLOGY);
            categoryTestRepository.save(categorie);
            crees++;
        }
        log.info("Catégories de biologie par défaut : {} créée(s) pour la succursale {}", crees, branchId);
        return categoryTestRepository.findAllByBranchIdAndDisciplineOrderByName(branchId, Discipline.BIOLOGY)
                .stream().map(mapper::toCategoryTestResponseDto).toList();
    }

    /** {@code null} (client qui n'envoie pas la discipline) vaut anatomie pathologique. */
    private static Discipline disciplineOuDefaut(Discipline discipline) {
        return discipline != null ? discipline : Discipline.PATHOLOGY;
    }

    /**
     * Les routes {@code /category-tests} sont partagées avec l'anapath et échappent
     * donc à l'intercepteur du module : c'est ici que la biologie se ferme.
     */
    private void exigerLeModuleBiologie() {
        if (!modules.isBiology()) {
            throw new BusinessException("Le module Biologie n'est pas activé sur ce laboratoire.");
        }
    }
}
