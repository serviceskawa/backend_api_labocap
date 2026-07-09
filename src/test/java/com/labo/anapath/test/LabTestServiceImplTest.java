package com.labo.anapath.test;

import com.labo.anapath.common.dto.PageResponse;
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

import java.math.BigDecimal;
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
class LabTestServiceImplTest {

    @Mock
    private LabTestRepository labTestRepository;

    @Mock
    private CategoryTestRepository categoryTestRepository;

    @Mock
    private UnitMeasurementRepository unitMeasurementRepository;

    @Mock
    private TestCatalogueMapper mapper;

    @InjectMocks
    private LabTestServiceImpl labTestService;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID ID = UUID.randomUUID();

    private LabTest buildEntity(String name) {
        LabTest lt = new LabTest();
        lt.setName(name);
        lt.setPrice(BigDecimal.valueOf(5000));
        lt.setStatus("ACTIF");
        lt.setBranchId(BRANCH_ID);
        return lt;
    }

    private LabTestResponseDto buildResponseDto(String name) {
        return new LabTestResponseDto(ID, name, null, BigDecimal.valueOf(5000), null, "ACTIF",
                null, null, null, null, BRANCH_ID, LocalDateTime.now());
    }

    @Test
    @DisplayName("create - crée une analyse et retourne le DTO")
    void create_success_returnsDto() {
        LabTestRequestDto dto = new LabTestRequestDto();
        dto.setName("NFS");
        dto.setPrice(BigDecimal.valueOf(5000));

        LabTest entity = buildEntity("NFS");
        LabTestResponseDto responseDto = buildResponseDto("NFS");

        when(labTestRepository.existsByNameIgnoreCaseAndBranchId("NFS", BRANCH_ID)).thenReturn(false);
        when(mapper.toLabTestEntity(dto)).thenReturn(entity);
        when(labTestRepository.save(any(LabTest.class))).thenReturn(entity);
        when(mapper.toLabTestResponseDto(entity)).thenReturn(responseDto);

        LabTestResponseDto result = labTestService.create(dto, BRANCH_ID);

        assertThat(result.name()).isEqualTo("NFS");
        verify(labTestRepository).save(entity);
    }

    @Test
    @DisplayName("create - nom en doublon → DuplicateResourceException")
    void create_duplicateName_throws409() {
        LabTestRequestDto dto = new LabTestRequestDto();
        dto.setName("NFS");
        dto.setPrice(BigDecimal.valueOf(5000));

        when(labTestRepository.existsByNameIgnoreCaseAndBranchId("NFS", BRANCH_ID)).thenReturn(true);

        assertThatThrownBy(() -> labTestService.create(dto, BRANCH_ID))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("findById - retourne le DTO si trouvé")
    void findById_found_returnsDto() {
        LabTest entity = buildEntity("NFS");
        LabTestResponseDto responseDto = buildResponseDto("NFS");

        when(labTestRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(mapper.toLabTestResponseDto(entity)).thenReturn(responseDto);

        LabTestResponseDto result = labTestService.findById(ID);

        assertThat(result.name()).isEqualTo("NFS");
    }

    @Test
    @DisplayName("findById - lève ResourceNotFoundException si inexistant")
    void findById_notFound_throws404() {
        when(labTestRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labTestService.findById(ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAll - retourne une page paginée")
    void findAll_returnsPaginatedResults() {
        LabTest entity = buildEntity("NFS");
        LabTestResponseDto dto = buildResponseDto("NFS");
        Page<LabTest> page = new PageImpl<>(List.of(entity));

        when(labTestRepository.findByFilters(any(UUID.class), any(), any(), any(Pageable.class))).thenReturn(page);
        when(mapper.toLabTestResponseDto(entity)).thenReturn(dto);

        PageResponse<LabTestResponseDto> result = labTestService.findAll(0, 20, null, null, BRANCH_ID);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("search - retourne les analyses dont le nom contient le terme")
    void search_returnsMatchingAnalyses() {
        LabTest entity = buildEntity("Numération Formule Sanguine");
        LabTestResponseDto dto = buildResponseDto("Numération Formule Sanguine");

        when(labTestRepository.findByNameContainingIgnoreCaseAndBranchId("Numération", BRANCH_ID))
                .thenReturn(List.of(entity));
        when(mapper.toLabTestResponseDto(entity)).thenReturn(dto);

        List<LabTestResponseDto> result = labTestService.search("Numération", BRANCH_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Numération Formule Sanguine");
    }

    @Test
    @DisplayName("search - aucun résultat → liste vide")
    void search_noMatch_returnsEmptyList() {
        when(labTestRepository.findByNameContainingIgnoreCaseAndBranchId("INEXISTANT", BRANCH_ID))
                .thenReturn(List.of());

        List<LabTestResponseDto> result = labTestService.search("INEXISTANT", BRANCH_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("delete - supprime l'analyse")
    void delete_callsRepositoryDelete() {
        LabTest entity = buildEntity("NFS");

        when(labTestRepository.findById(ID)).thenReturn(Optional.of(entity));

        labTestService.delete(ID);

        verify(labTestRepository).delete(entity);
    }

    @Test
    @DisplayName("update - met à jour l'analyse et retourne le DTO")
    void update_success_returnsUpdatedDto() {
        LabTestRequestDto dto = new LabTestRequestDto();
        dto.setName("NFS Modifié");
        dto.setPrice(BigDecimal.valueOf(6000));

        LabTest entity = buildEntity("NFS");
        LabTestResponseDto responseDto = buildResponseDto("NFS Modifié");

        when(labTestRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(labTestRepository.save(any(LabTest.class))).thenReturn(entity);
        when(mapper.toLabTestResponseDto(entity)).thenReturn(responseDto);

        LabTestResponseDto result = labTestService.update(ID, dto);

        assertThat(result.name()).isEqualTo("NFS Modifié");
        verify(mapper).updateLabTestFromDto(dto, entity);
    }
}
