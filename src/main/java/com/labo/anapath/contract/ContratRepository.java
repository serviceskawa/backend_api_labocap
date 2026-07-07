package com.labo.anapath.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface ContratRepository extends JpaRepository<Contrat, UUID> {

    Page<Contrat> findByBranchId(UUID branchId, Pageable pageable);

    java.util.Optional<Contrat> findByIdAndBranchId(UUID id, UUID branchId);

    boolean existsByClientId(UUID clientId);

    // Dashboard KPIs
    long countByBranchId(UUID branchId);

    // CAST(... AS string) sur chaque test "IS NULL" : sans cela PostgreSQL n'arrive
    // pas à inférer le type du paramètre (« could not determine data type of parameter »)
    // et l'endpoint renvoie 500 dès qu'un filtre est absent (cas du menu déroulant contrat).
    @Query("SELECT c FROM Contrat c WHERE c.branchId = :branchId " +
           "AND (CAST(:status AS string) IS NULL OR c.status = :status) " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (CAST(:dateFrom AS string) IS NULL OR c.startDate >= :dateFrom) " +
           "AND (CAST(:dateTo AS string) IS NULL OR c.startDate <= :dateTo)")
    Page<Contrat> findWithFilters(@Param("branchId") UUID branchId,
                                  @Param("status") String status,
                                  @Param("search") String search,
                                  @Param("dateFrom") LocalDate dateFrom,
                                  @Param("dateTo") LocalDate dateTo,
                                  Pageable pageable);
}
