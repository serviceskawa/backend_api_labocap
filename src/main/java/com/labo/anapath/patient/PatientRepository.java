package com.labo.anapath.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'accès aux données des patients ({@link Patient}).
 * <p>
 * Implémente également {@link JpaSpecificationExecutor} pour permettre des requêtes
 * dynamiques à base de critères si nécessaire. La recherche textuelle multi-champs
 * (prénom, nom, téléphone, code) est fournie par une requête JPQL dédiée.
 * Toutes les requêtes sont filtrées par {@code branchId}.
 * </p>
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID>, JpaSpecificationExecutor<Patient> {

    /**
     * Retourne les patients d'une agence donnée, paginés.
     *
     * @param branchId identifiant de l'agence
     * @param pageable paramètres de pagination et de tri
     * @return page de patients
     */
    Page<Patient> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Recherche des patients dans une agence correspondant à un terme de recherche.
     * La correspondance est vérifiée sur le prénom, le nom (insensible à la casse),
     * le numéro de téléphone principal et le code patient.
     *
     * @param branchId identifiant de l'agence
     * @param search   terme de recherche partiel
     * @param pageable paramètres de pagination et de tri
     * @return page de patients correspondants
     */
    @Query("SELECT p FROM Patient p WHERE p.branchId = :branchId " +
           "AND (LOWER(p.firstname) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR p.telephone1 LIKE CONCAT('%', :search, '%') " +
           "OR p.code LIKE CONCAT('%', :search, '%'))")
    Page<Patient> findByBranchIdAndSearchTerm(@Param("branchId") UUID branchId,
                                               @Param("search") String search,
                                               Pageable pageable);

    /**
     * Recherche un patient par son identifiant et sa branche.
     * Assure l'isolation multi-tenant : un patient ne peut être accédé que par sa branche.
     *
     * @param id       identifiant UUID du patient
     * @param branchId identifiant de l'agence
     * @return le patient s'il appartient à l'agence, sinon vide
     */
    java.util.Optional<Patient> findByIdAndBranchId(UUID id, UUID branchId);

    // Il y avait ici un `existsByTelephone1AndBranchId`, qui servait à refuser
    // un patient dont le numéro était déjà pris. La règle a été retirée : un
    // numéro n'identifie personne dans un laboratoire, où une même ligne sert
    // souvent à toute une famille. La méthode part avec elle — la laisser
    // inviterait à rétablir la règle sans en reprendre la question.

    /**
     * Vérifie qu'aucun patient de la même agence n'a déjà ce code.
     *
     * @param code     code patient à vérifier
     * @param branchId identifiant de l'agence
     * @return {@code true} si un doublon existe
     */
    boolean existsByCodeAndBranchId(String code, UUID branchId);

    /**
     * Vérifie l'unicité du code patient au sein d'une agence, en excluant le patient identifié.
     * Utilisé lors d'une mise à jour pour ne pas bloquer le patient sur son propre code.
     *
     * @param code     code patient à vérifier
     * @param branchId identifiant de l'agence
     * @param id       identifiant du patient à exclure
     * @return {@code true} si un autre patient de l'agence porte déjà ce code
     */
    boolean existsByCodeAndBranchIdAndIdNot(String code, UUID branchId, UUID id);

    // Dashboard KPIs
    long countByBranchId(UUID branchId);

    @Query("SELECT COUNT(p) FROM Patient p WHERE p.branchId = :branchId AND p.createdAt >= :start AND p.createdAt <= :end")
    long countByBranchIdAndCreatedAtBetween(@Param("branchId") UUID branchId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);
}
