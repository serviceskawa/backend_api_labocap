package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour {@link Antibiotic}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface AntibioticRepository extends JpaRepository<Antibiotic, UUID> {

    /** Antibiotiques de la succursale, dans l'ordre de l'antibiogramme puis par nom. */
    List<Antibiotic> findByBranchIdOrderByPositionAscNameAsc(UUID branchId);

    /** Antibiotique de la succursale, pour l'isolation multi-tenant. */
    Optional<Antibiotic> findByIdAndBranchId(UUID id, UUID branchId);

    /** Nom déjà utilisé dans la succursale (insensible à la casse). */
    boolean existsByNameIgnoreCaseAndBranchId(String name, UUID branchId);

    /** Nom déjà utilisé par un autre antibiotique de la succursale. */
    boolean existsByNameIgnoreCaseAndBranchIdAndIdNot(String name, UUID branchId, UUID id);

    /** Code déjà utilisé dans la succursale (insensible à la casse). */
    boolean existsByCodeIgnoreCaseAndBranchId(String code, UUID branchId);

    /** Code déjà utilisé par un autre antibiotique de la succursale. */
    boolean existsByCodeIgnoreCaseAndBranchIdAndIdNot(String code, UUID branchId, UUID id);
}
