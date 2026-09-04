package com.labo.anapath.mobile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MobileDeviceRepository extends JpaRepository<MobileDevice, UUID> {

    /**
     * Appareil encore en service pour cet identifiant.
     *
     * <p>Le filtre sur la révocation est porté par la requête et non par
     * l'appelant : oublier de le poser sur un chemin d'authentification
     * laisserait un téléphone révoqué continuer d'ouvrir des sessions.</p>
     */
    Optional<MobileDevice> findByIdAndRevokedAtIsNull(UUID id);

    List<MobileDevice> findByUserIdOrderByEnrolledAtDesc(UUID userId);

    List<MobileDevice> findByBranchIdOrderByEnrolledAtDesc(UUID branchId);

    /**
     * Les jetons de notification des appareils vivants de ces personnes.
     *
     * <p>Une requête pour tout le monde plutôt qu'une par destinataire : un fil
     * a rarement plus de quelques participants, mais chacun peut avoir deux
     * téléphones, et poster un message ne doit pas coûter dix requêtes.</p>
     *
     * <p>Les appareils révoqués sont écartés : couper un téléphone doit couper
     * ses notifications, sans qu'on ait à y penser ailleurs.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d.pushToken FROM MobileDevice d
            WHERE d.userId IN :userIds
              AND d.revokedAt IS NULL
              AND d.pushToken IS NOT NULL
            """)
    java.util.List<String> jetonsDe(
            @org.springframework.data.repository.query.Param("userIds")
            java.util.Collection<java.util.UUID> userIds);
}
