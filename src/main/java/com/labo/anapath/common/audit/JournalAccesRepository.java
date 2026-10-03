package com.labo.anapath.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.UUID;

public interface JournalAccesRepository extends JpaRepository<JournalAcces, UUID>, JpaSpecificationExecutor<JournalAcces> {
    long deleteByAtBefore(LocalDateTime avant);
}
