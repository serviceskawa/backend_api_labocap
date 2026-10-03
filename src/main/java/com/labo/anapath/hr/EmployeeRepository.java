package com.labo.anapath.hr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'accès aux données des employés.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /** Dans l'agence donnée seulement : un identifiant d'ailleurs est introuvable. */
    Optional<Employee> findByIdAndBranchId(UUID id, UUID branchId);

    /**
     * Retourne une page d'employés filtrés par filiale.
     *
     * @param branchId identifiant de la filiale
     * @param pageable paramètres de pagination et de tri
     * @return page d'employés de la filiale
     */
    Page<Employee> findByBranchId(UUID branchId, Pageable pageable);
}
