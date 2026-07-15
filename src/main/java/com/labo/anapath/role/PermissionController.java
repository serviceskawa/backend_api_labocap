package com.labo.anapath.role;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST exposant la liste des permissions disponibles dans le système.
 *
 * <p>Les permissions sont en lecture seule via l'API : leur création et leur
 * modification sont gérées par les scripts de seeding de base de données.
 * Tout utilisateur authentifié peut consulter la liste pour alimenter
 * les interfaces de gestion des rôles.</p>
 *
 * <p>Base URL : {@code /api/v1/permissions}</p>
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    /**
     * Retourne la liste complète de toutes les permissions du système.
     * Accessible à tout utilisateur authentifié (pour alimenter les écrans
     * d'assignation de permissions aux rôles).
     *
     * @return liste de {@link PermissionResponseDto}
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> findAll() {
        List<PermissionResponseDto> result = permissionRepository.findAll()
                .stream().map(permissionMapper::toResponseDto).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Crée une nouvelle permission. Le slug est normalisé (minuscules, sans
     * espaces superflus) et doit être unique.
     *
     * @param dto données validées (name + slug)
     * @return la permission créée avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('create-permissions')")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> create(
            @Valid @RequestBody PermissionRequestDto dto) {
        String slug = normalizeSlug(dto.getSlug());
        if (permissionRepository.findBySlug(slug).isPresent()) {
            throw new DuplicateResourceException(
                    "Une permission avec le slug '" + slug + "' existe déjà.");
        }
        Permission permission = new Permission();
        permission.setName(dto.getName().trim());
        permission.setSlug(slug);
        Permission saved = permissionRepository.save(permission);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Permission créée avec succès",
                        permissionMapper.toResponseDto(saved)));
    }

    /**
     * Met à jour le libellé et/ou le slug d'une permission existante.
     *
     * @param id  identifiant de la permission à modifier
     * @param dto nouvelles données validées
     * @return la permission mise à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-permissions')")
    public ResponseEntity<ApiResponse<PermissionResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PermissionRequestDto dto) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id));
        String slug = normalizeSlug(dto.getSlug());
        if (!slug.equals(permission.getSlug())
                && permissionRepository.findBySlug(slug).isPresent()) {
            throw new DuplicateResourceException(
                    "Une permission avec le slug '" + slug + "' existe déjà.");
        }
        permission.setName(dto.getName().trim());
        permission.setSlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Permission mise à jour",
                permissionMapper.toResponseDto(permissionRepository.save(permission))));
    }

    /**
     * Supprime une permission par son identifiant.
     *
     * @param id identifiant de la permission à supprimer
     * @return réponse vide avec message de confirmation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-permissions')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        if (!permissionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Permission", id);
        }
        permissionRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Permission supprimée", null));
    }

    /** Normalise un slug : trim + minuscules. */
    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }
}
