package com.labo.anapath.biology;

import java.util.List;
import java.util.UUID;

/**
 * Options de culture : le référentiel de la succursale, et le choix de celles que
 * retient chaque analyse CULTURE.
 */
public interface CultureOptionService {

    /**
     * @param branchId succursale courante
     * @return options de culture, par position puis par nom
     */
    List<CultureOptionResponseDto> findAll(UUID branchId);

    /**
     * @param dto      données de l'option
     * @param branchId succursale courante
     * @return l'option créée
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si le nom est déjà pris
     */
    CultureOptionResponseDto create(CultureOptionRequestDto dto, UUID branchId);

    /**
     * @param id       identifiant de l'option
     * @param dto      nouvelles données
     * @param branchId succursale courante
     * @return l'option modifiée
     */
    CultureOptionResponseDto update(UUID id, CultureOptionRequestDto dto, UUID branchId);

    /**
     * Suppression logique de l'option et de ses rattachements aux analyses.
     *
     * @param id       identifiant de l'option
     * @param branchId succursale courante
     */
    void delete(UUID id, UUID branchId);

    /**
     * @param labTestId identifiant d'une analyse CULTURE
     * @param branchId  succursale courante
     * @return options retenues par l'analyse, dans l'ordre d'affichage
     */
    List<LabTestCultureOptionResponseDto> findByLabTest(UUID labTestId, UUID branchId);

    /**
     * Remplace les options retenues par une analyse CULTURE.
     *
     * @param labTestId        identifiant d'une analyse CULTURE
     * @param cultureOptionIds options, dans l'ordre d'affichage (sans doublon)
     * @param branchId         succursale courante
     * @return options retenues après l'opération
     * @throws com.labo.anapath.common.exception.BusinessException si l'analyse n'est pas BIOLOGY/CULTURE
     *         ou si la liste contient un doublon
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si une option n'existe pas
     */
    List<LabTestCultureOptionResponseDto> setForLabTest(UUID labTestId, List<UUID> cultureOptionIds, UUID branchId);
}
