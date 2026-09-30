package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyCultureOption}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface BiologyCultureOptionRepository extends JpaRepository<BiologyCultureOption, UUID> {

    /** Options de culture de la succursale, par position puis par nom. */
    List<BiologyCultureOption> findByBranchIdOrderByPositionAscNameAsc(UUID branchId);

    /** Option de la succursale, pour l'isolation multi-tenant. */
    Optional<BiologyCultureOption> findByIdAndBranchId(UUID id, UUID branchId);

    /** Options de la succursale parmi les identifiants donnés. */
    List<BiologyCultureOption> findByIdInAndBranchId(Collection<UUID> ids, UUID branchId);

    /** Nom déjà utilisé dans la succursale (insensible à la casse). */
    boolean existsByNameIgnoreCaseAndBranchId(String name, UUID branchId);

    /** Nom déjà utilisé par une autre option de la succursale. */
    boolean existsByNameIgnoreCaseAndBranchIdAndIdNot(String name, UUID branchId, UUID id);
}
