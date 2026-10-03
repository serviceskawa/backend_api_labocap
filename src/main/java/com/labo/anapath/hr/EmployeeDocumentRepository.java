package com.labo.anapath.hr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

    /** Dans l'agence donnée seulement : un identifiant d'ailleurs est introuvable. */
    Optional<EmployeeDocument> findByIdAndBranchId(UUID id, UUID branchId);

    Page<EmployeeDocument> findByEmployeeId(UUID employeeId, Pageable pageable);
}
