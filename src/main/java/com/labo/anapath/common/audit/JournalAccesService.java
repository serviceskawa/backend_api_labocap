package com.labo.anapath.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Écrit le journal des consultations, en dehors de la requête : la lecture
 * d'un dossier ne doit pas attendre l'écriture de sa trace, et une trace
 * perdue se journalise en WARN plutôt que de faire échouer la lecture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalAccesService {

    private final JournalAccesRepository repository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enregistrer(UUID userId, UUID branchId, JournalAcces.Action action,
                            JournalAcces.Entite entite, String entityId, String ip, String userAgentHash) {
        try {
            JournalAcces ligne = new JournalAcces();
            ligne.setUserId(userId);
            ligne.setBranchId(branchId);
            ligne.setAction(action);
            ligne.setEntityType(entite);
            ligne.setEntityId(entityId);
            ligne.setIp(ip);
            ligne.setUserAgentHash(userAgentHash);
            repository.save(ligne);
        } catch (Exception e) {
            log.warn("Trace de consultation perdue : {} {} {} par {} — {}", action, entite, entityId, userId, e.getMessage());
        }
    }
}
