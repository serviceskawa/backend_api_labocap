package com.labo.anapath.mobile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MobileEnrollmentCodeRepository extends JpaRepository<MobileEnrollmentCode, UUID> {

    /**
     * Codes encore inemployés d'un utilisateur, du plus récent au plus ancien.
     *
     * <p>Le code étant haché, on ne peut pas le retrouver par recherche directe :
     * l'enrôlement désigne l'utilisateur, on charge ses codes vivants, puis on
     * les vérifie un à un. Leur nombre est nécessairement minime — ils expirent
     * vite et servent une fois.</p>
     */
    List<MobileEnrollmentCode> findByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
