package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link LabTestCultureOption}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface LabTestCultureOptionRepository extends JpaRepository<LabTestCultureOption, UUID> {

    /** Options retenues par une analyse, dans l'ordre d'affichage, option chargée d'emblée. */
    @EntityGraph(attributePaths = "cultureOption")
    List<LabTestCultureOption> findByLabTest_IdOrderByPositionAsc(UUID labTestId);

    /** Analyses qui retiennent une option (retrait de l'option du référentiel). */
    List<LabTestCultureOption> findByCultureOption_Id(UUID cultureOptionId);
}
