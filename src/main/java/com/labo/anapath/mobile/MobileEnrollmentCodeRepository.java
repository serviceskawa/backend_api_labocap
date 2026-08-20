package com.labo.anapath.mobile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MobileEnrollmentCodeRepository extends JpaRepository<MobileEnrollmentCode, UUID> {

    /**
     * Codes d'un utilisateur, du plus récent au plus ancien.
     *
     * <p>Le code étant haché, on ne peut pas le retrouver par recherche directe :
     * l'enrôlement désigne l'utilisateur, on charge ses codes, puis on les
     * vérifie un à un. Leur nombre reste minime — créer un code révoque le
     * précédent.</p>
     *
     * <p>Ne filtre plus sur {@code usedAt} : un code sert désormais plusieurs
     * fois, et l'écarter dès son premier usage le rendait inutilisable pour le
     * deuxième téléphone.</p>
     */
    List<MobileEnrollmentCode> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
