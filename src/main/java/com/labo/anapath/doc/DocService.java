package com.labo.anapath.doc;

import com.labo.anapath.common.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocService {
    PageResponse<DocResponseDto> findAll(int page, int size, UUID branchId);

    /** Documents d'une catégorie donnée (volet droit de l'explorateur). */
    List<DocResponseDto> findByCategory(UUID categoryId, UUID branchId);

    DocResponseDto findById(UUID id);
    DocResponseDto create(String title, UUID documentationCategoryId, MultipartFile file, UUID userId, UUID branchId);

    /**
     * Édite le titre d'un document et, si un fichier est fourni, remplace la pièce
     * jointe courante (sans créer de nouvelle version). Calque Laravel `doc.update`.
     */
    DocResponseDto updateTitle(UUID id, String title, MultipartFile file);
    DocVersionResponseDto addVersion(UUID docId, String title, MultipartFile file, UUID userId, UUID branchId);
    List<DocVersionResponseDto> getVersions(UUID docId);
    void delete(UUID id);

    /** Partage un document avec un rôle et notifie par email tous les utilisateurs de ce rôle. */
    DocResponseDto share(UUID docId, UUID roleId, UUID branchId);

    /** Documents partagés avec l'utilisateur courant (via ses rôles). */
    PageResponse<DocResponseDto> findSharedWithMe(int page, int size, UUID userId, UUID branchId);

    /** Documents les plus récents de la branche (par date de création). */
    List<DocResponseDto> findRecent(UUID branchId, int limit);

    /** Documents en corbeille (supprimés logiquement) de la branche. */
    PageResponse<DocResponseDto> findTrash(int page, int size, UUID branchId);

    /** Restaure un document depuis la corbeille. */
    DocResponseDto restore(UUID id);

    /** Supprime définitivement un document (et ses versions / fichiers). */
    void permanentDelete(UUID id);
}
