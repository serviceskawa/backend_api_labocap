package com.labo.anapath.test;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper MapStruct centralisant toutes les conversions entité ↔ DTO du catalogue d'analyses.
 *
 * <p>Ce mapper unique regroupe les conversions pour les cinq entités du catalogue :
 * {@link CategoryTest}, {@link LabTest}, {@link UnitMeasurement}, {@link TypeOrder}
 * et {@link DataCode}, afin d'éviter la multiplication des interfaces de mapping.</p>
 *
 * <p>Les associations JPA ({@code categoryTest}, {@code unitMeasurement}) de {@link LabTest}
 * sont toujours ignorées lors de la conversion DTO → entité ; elles sont résolues
 * manuellement dans le service après chargement depuis la base de données.</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TestCatalogueMapper {

    // ------------------------------------------------------------------ CategoryTest

    /**
     * Convertit une entité {@link CategoryTest} en {@link CategoryTestResponseDto}.
     *
     * @param entity entité source
     * @return DTO de réponse
     */
    CategoryTestResponseDto toCategoryTestResponseDto(CategoryTest entity);

    /**
     * Convertit un {@link CategoryTestRequestDto} en entité {@link CategoryTest}.
     *
     * La discipline est fixée par le service (défaut PATHOLOGY), pas recopiée.
     *
     * @param dto DTO source
     * @return entité partiellement initialisée (sans branchId)
     */
    @Mapping(target = "discipline", ignore = true)
    CategoryTest toCategoryTestEntity(CategoryTestRequestDto dto);

    /**
     * Met à jour une entité {@link CategoryTest} existante à partir d'un DTO.
     * Les propriétés nulles du DTO sont ignorées. Les champs d'audit et la
     * discipline (fixée à la création) sont protégés.
     *
     * @param dto    DTO source
     * @param entity entité cible
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "discipline", ignore = true)
    void updateCategoryTestFromDto(CategoryTestRequestDto dto, @MappingTarget CategoryTest entity);

    // ------------------------------------------------------------------ UnitMeasurement

    /**
     * Convertit une entité {@link UnitMeasurement} en {@link UnitMeasurementResponseDto}.
     *
     * @param entity entité source
     * @return DTO de réponse
     */
    UnitMeasurementResponseDto toUnitMeasurementResponseDto(UnitMeasurement entity);

    /**
     * Convertit un {@link UnitMeasurementRequestDto} en entité {@link UnitMeasurement}.
     *
     * @param dto DTO source
     * @return entité partiellement initialisée (sans branchId)
     */
    UnitMeasurement toUnitMeasurementEntity(UnitMeasurementRequestDto dto);

    // ------------------------------------------------------------------ TypeOrder

    /**
     * Convertit une entité {@link TypeOrder} en {@link TypeOrderResponseDto}.
     *
     * @param entity entité source
     * @return DTO de réponse
     */
    TypeOrderResponseDto toTypeOrderResponseDto(TypeOrder entity);

    /**
     * Convertit un {@link TypeOrderRequestDto} en entité {@link TypeOrder}.
     *
     * @param dto DTO source
     * @return entité partiellement initialisée (sans branchId)
     */
    TypeOrder toTypeOrderEntity(TypeOrderRequestDto dto);

    // ------------------------------------------------------------------ LabTest

    /**
     * Convertit une entité {@link LabTest} en {@link LabTestResponseDto}.
     * Les informations de catégorie et d'unité sont dénormalisées (id + nom).
     *
     * @param entity entité source
     * @return DTO de réponse avec catégorie et unité dénormalisées
     */
    @Mapping(target = "categoryTestId", source = "categoryTest.id")
    @Mapping(target = "categoryTestName", source = "categoryTest.name")
    @Mapping(target = "unitMeasurementId", source = "unitMeasurement.id")
    @Mapping(target = "unitMeasurementName", source = "unitMeasurement.name")
    LabTestResponseDto toLabTestResponseDto(LabTest entity);

    /**
     * Convertit un {@link LabTestRequestDto} en entité {@link LabTest}.
     * Les associations catégorie et unité sont ignorées et résolues manuellement dans le service,
     * comme la discipline et la nature biologique, dont le service fixe les défauts.
     *
     * @param dto DTO source
     * @return entité partiellement initialisée (sans branchId ni associations)
     */
    @Mapping(target = "categoryTest", ignore = true)
    @Mapping(target = "unitMeasurement", ignore = true)
    @Mapping(target = "discipline", ignore = true)
    @Mapping(target = "biologyKind", ignore = true)
    LabTest toLabTestEntity(LabTestRequestDto dto);

    /**
     * Met à jour une entité {@link LabTest} existante à partir d'un DTO.
     * Les propriétés nulles du DTO sont ignorées. Les associations, les champs d'audit,
     * la discipline et la nature biologique (fixées à la création) sont protégés.
     *
     * @param dto    DTO source
     * @param entity entité cible
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "categoryTest", ignore = true)
    @Mapping(target = "unitMeasurement", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "discipline", ignore = true)
    @Mapping(target = "biologyKind", ignore = true)
    void updateLabTestFromDto(LabTestRequestDto dto, @MappingTarget LabTest entity);

    // ------------------------------------------------------------------ DataCode

    /**
     * Convertit une entité {@link DataCode} en {@link DataCodeResponseDto}.
     *
     * @param entity entité source
     * @return DTO de réponse
     */
    DataCodeResponseDto toDataCodeResponseDto(DataCode entity);

    /**
     * Convertit un {@link DataCodeRequestDto} en entité {@link DataCode}.
     *
     * @param dto DTO source
     * @return entité partiellement initialisée (sans branchId)
     */
    DataCode toDataCodeEntity(DataCodeRequestDto dto);
}
