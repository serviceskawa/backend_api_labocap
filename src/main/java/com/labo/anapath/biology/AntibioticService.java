package com.labo.anapath.biology;

import java.util.List;
import java.util.UUID;

/**
 * Référentiel des antibiotiques de l'antibiogramme, par succursale.
 */
public interface AntibioticService {

    /**
     * @param branchId succursale courante
     * @return antibiotiques, dans l'ordre de l'antibiogramme puis par nom
     */
    List<AntibioticResponseDto> findAll(UUID branchId);

    /**
     * @param dto      données de l'antibiotique
     * @param branchId succursale courante
     * @return l'antibiotique créé
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si le nom ou le code est déjà pris
     */
    AntibioticResponseDto create(AntibioticRequestDto dto, UUID branchId);

    /**
     * @param id       identifiant de l'antibiotique
     * @param dto      nouvelles données
     * @param branchId succursale courante
     * @return l'antibiotique modifié
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException  s'il n'existe pas dans la succursale
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si le nom ou le code est déjà pris
     */
    AntibioticResponseDto update(UUID id, AntibioticRequestDto dto, UUID branchId);

    /**
     * Suppression logique : les antibiogrammes déjà rendus gardent le libellé.
     *
     * @param id       identifiant de l'antibiotique
     * @param branchId succursale courante
     */
    void delete(UUID id, UUID branchId);
}
