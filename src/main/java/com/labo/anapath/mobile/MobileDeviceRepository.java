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
}
