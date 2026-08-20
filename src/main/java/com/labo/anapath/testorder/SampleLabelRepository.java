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
    List<SampleLabel> findByBranchIdAndDeletedAtIsNullOrderByValueAsc(UUID branchId);

    /** Sans distinguer la casse : « L1 » et « l1 » sont le même contenant. */
    @Query("SELECT s FROM SampleLabel s WHERE s.branchId = :branchId "
            + "AND s.deletedAt IS NULL AND UPPER(s.value) = UPPER(:value)")
    Optional<SampleLabel> chercher(@Param("branchId") UUID branchId,
                                   @Param("value") String value);

    /**
     * Combien de demandes portent déjà cette étiquette.
     *
     * <p>Les étiquettes d'une ligne sont sérialisées en tableau JSON ; on
     * cherche donc le jeton entre guillemets. Les guillemets fermants évitent
     * qu'« L1 » se reconnaisse dans « L12 ».</p>
     *
     * <p>Sert à prévenir avant de retirer ou de renommer : une étiquette portée
     * par deux cents demandes ne se corrige pas d'un revers de main.</p>
     */
    @Query(value = "SELECT COUNT(*) FROM test_order_assignment_details d "
            + "WHERE d.branch_id = :branchId AND d.deleted_at IS NULL "
            + "AND d.labels LIKE CONCAT('%\"', :value, '\"%')",
            nativeQuery = true)
    long compterUsages(@Param("branchId") UUID branchId,
                       @Param("value") String value);
}
