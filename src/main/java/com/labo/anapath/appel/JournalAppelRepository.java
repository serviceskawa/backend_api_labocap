package com.labo.anapath.appel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalAppelRepository extends JpaRepository<JournalAppel, UUID> {

    /** Les appels d'un dossier, du plus récent au plus ancien. */
    List<JournalAppel> findByTestOrderIdOrderByDebutDesc(UUID testOrderId);
}
