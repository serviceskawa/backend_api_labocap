package com.labo.anapath.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FichierStockeRepository extends JpaRepository<FichierStocke, UUID> {

    /** Enregistre le rattachement ; rejouer sur le même chemin ne crée rien de plus. */
    default void rattacher(String path, String entityType, UUID entityId, UUID branchId) {
        if (path == null || path.isBlank()) return;
        save(new FichierStocke(path, entityType, entityId, branchId));
    }

    default Optional<FichierStocke> parChemin(String path) {
        UUID id = FichierStocke.idPour(path);
        return id == null ? Optional.empty() : findById(id);
    }
}
