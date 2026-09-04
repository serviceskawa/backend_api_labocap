package com.labo.anapath.mobile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JournalActionMobileRepository extends JpaRepository<JournalActionMobile, UUID> {

    /** Ce qu'une personne a fait, du plus récent au plus ancien. */
    Page<JournalActionMobile> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    /** Ce qui s'est passé sur une branche, tous agents confondus. */
    Page<JournalActionMobile> findByBranchIdOrderByOccurredAtDesc(UUID branchId, Pageable pageable);
}
