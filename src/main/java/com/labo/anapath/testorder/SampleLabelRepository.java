package com.labo.anapath.testorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SampleLabelRepository extends JpaRepository<SampleLabel, UUID> {

    /**
     * Le vocabulaire d'une branche, dans l'ordre où on le lit.
     *
     * <p>Tri alphabétique : « L1, L2, L3 » se parcourt du regard, là qu'un tri
     * par date de création placerait les plus récentes en tête et déplacerait
     * les repères d'une semaine à l'autre.</p>
     */
    List<SampleLabel> findByBranchIdOrderByValueAsc(UUID branchId);

    /** Sans distinguer la casse : « L1 » et « l1 » sont le même contenant. */
    @Query("SELECT s FROM SampleLabel s WHERE s.branchId = :branchId "
            + "AND UPPER(s.value) = UPPER(:value)")
    Optional<SampleLabel> chercher(@Param("branchId") UUID branchId,
                                   @Param("value") String value);
}
