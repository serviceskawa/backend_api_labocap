package com.labo.anapath.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PerimetreDeValidationRepository extends JpaRepository<PerimetreDeValidation, UUID> {

    List<PerimetreDeValidation> findByUserIdOrderByTypeOrderTitleAsc(UUID userId);

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    /**
     * Ce compte a-t-il le droit de valider un examen portant ce libellé ?
     *
     * <p>La comparaison porte sur le <strong>libellé</strong> et non sur
     * l'identifiant, parce que {@code type_orders} contient des doublons hérités
     * de la reprise Laravel : douze lignes pour six types, chaque libellé
     * existant deux fois. Accorder un identifiant et comparer sur l'identifiant
     * laisserait passer les demandes rattachées au jumeau — un refus
     * incompréhensible sur une demande qui ressemble en tout point à celles que
     * l'agent valide chaque jour.</p>
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM PerimetreDeValidation p
            WHERE p.user.id = :userId
              AND LOWER(TRIM(p.typeOrder.title)) = LOWER(TRIM(:titre))
            """)
    boolean couvreLeType(@Param("userId") UUID userId, @Param("titre") String titre);

    /** Les libellés couverts, pour les afficher ou les expliquer dans un refus. */
    @Query("""
            SELECT DISTINCT p.typeOrder.title FROM PerimetreDeValidation p
            WHERE p.user.id = :userId
            ORDER BY p.typeOrder.title
            """)
    List<String> libellesCouverts(@Param("userId") UUID userId);
}
