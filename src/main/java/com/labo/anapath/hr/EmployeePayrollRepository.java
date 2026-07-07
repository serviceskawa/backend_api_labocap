package com.labo.anapath.hr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'accès aux fiches de paie des employés.
 */
@Repository
public interface EmployeePayrollRepository extends JpaRepository<EmployeePayroll, UUID> {

    /**
     * Retourne une page de fiches de paie filtrées par identifiant d'employé.
     *
     * @param employeeId identifiant UUID de l'employé
     * @param pageable   paramètres de pagination
     * @return page de fiches de paie
     */
    Page<EmployeePayroll> findByEmployeeId(UUID employeeId, Pageable pageable);

    /**
     * Retourne une fiche de paie par son identifiant ET celui de son employé,
     * garantissant la cohérence employé/fiche (utilisé pour la génération PDF).
     */
    Optional<EmployeePayroll> findByIdAndEmployeeId(UUID id, UUID employeeId);
}
