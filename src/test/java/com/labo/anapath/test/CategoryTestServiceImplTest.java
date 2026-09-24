package com.labo.anapath.test;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryTestServiceImplTest {

    @Mock
    private CategoryTestRepository categoryTestRepository;

    @Mock
    private LabTestRepository labTestRepository;

    @Mock
    private TestCatalogueMapper mapper;

    @InjectMocks
    private CategoryTestServiceImpl categoryTestService;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID ID = UUID.randomUUID();

    private CategoryTest buildEntity(String name) {
        CategoryTest cat = new CategoryTest();
        cat.setName(name);
        cat.setBranchId(BRANCH_ID);
        return cat;
    }

    private CategoryTestResponseDto buildResponseDto(String name) {
        return new CategoryTestResponseDto(ID, name, null, BRANCH_ID, LocalDateTime.now(),
                com.labo.anapath.common.Discipline.PATHOLOGY);
    }

    @Test
    @DisplayName("create - crée une catégorie et retourne le DTO")
    void create_success_returnsDto() {
        CategoryTestRequestDto dto = new CategoryTestRequestDto();
        dto.setName("Cytologie");

        CategoryTest entity = buildEntity("Cytologie");
        CategoryTestResponseDto responseDto = buildResponseDto("Cytologie");

        when(categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline("Cytologie", BRANCH_ID,
                com.labo.anapath.common.Discipline.PATHOLOGY)).thenReturn(false);
        when(mapper.toCategoryTestEntity(dto)).thenReturn(entity);
        when(categoryTestRepository.save(any(CategoryTest.class))).thenReturn(entity);
        when(mapper.toCategoryTestResponseDto(entity)).thenReturn(responseDto);

        CategoryTestResponseDto result = categoryTestService.create(dto, BRANCH_ID);

        assertThat(result.name()).isEqualTo("Cytologie");
        verify(categoryTestRepository).save(entity);
    }

    @Test
    @DisplayName("create - nom en doublon → DuplicateResourceException")
    void create_duplicateName_throws409() {
        CategoryTestRequestDto dto = new CategoryTestRequestDto();
        dto.setName("Cytologie");

        when(categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline("Cytologie", BRANCH_ID,
                com.labo.anapath.common.Discipline.PATHOLOGY)).thenReturn(true);

        assertThatThrownBy(() -> categoryTestService.create(dto, BRANCH_ID))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("findAll - retourne une page paginée")
    void findAll_returnsPaginatedResults() {
        CategoryTest entity = buildEntity("Cytologie");
        CategoryTestResponseDto dto = buildResponseDto("Cytologie");
        Page<CategoryTest> page = new PageImpl<>(List.of(entity));

        when(categoryTestRepository.findByBranchIdAndDiscipline(any(UUID.class),
                org.mockito.ArgumentMatchers.eq(com.labo.anapath.common.Discipline.PATHOLOGY), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toCategoryTestResponseDto(entity)).thenReturn(dto);

        PageResponse<CategoryTestResponseDto> result = categoryTestService.findAll(0, 20, null, BRANCH_ID);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("findById - lève ResourceNotFoundException si inexistant")
    void findById_notFound_throws404() {
        when(categoryTestRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryTestService.findById(ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete - supprime la catégorie si aucun lab_test lié")
    void delete_callsRepositoryDelete() {
        CategoryTest entity = buildEntity("Cytologie");

        when(categoryTestRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(labTestRepository.existsByCategoryTest(entity)).thenReturn(false);

        categoryTestService.delete(ID);

        verify(categoryTestRepository).delete(entity);
    }

    @Test
    @DisplayName("delete - lève BusinessException si des lab_tests sont liés")
    void delete_withLinkedLabTests_throwsBusinessException() {
        CategoryTest entity = buildEntity("Cytologie");

        when(categoryTestRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(labTestRepository.existsByCategoryTest(entity)).thenReturn(true);

        assertThatThrownBy(() -> categoryTestService.delete(ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("référencée par des analyses");
    }

    @Test
    @DisplayName("delete - catégorie inexistante → ResourceNotFoundException")
    void delete_notFound_throws404() {
        when(categoryTestRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryTestService.delete(ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
