package com.labo.anapath.role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO de réponse représentant un rôle exposé par l'API.
 *
 * <p>Inclut la liste des permissions associées pour permettre à l'interface
 * d'afficher les droits accordés par ce rôle sans requête supplémentaire.</p>
 *
 * @param id           identifiant unique du rôle
 * @param name         nom lisible du rôle
 * @param slug         slug technique unique (utilisé dans les vérifications de sécurité)
 * @param description  description optionnelle du rôle
 * @param isAssignable indique si le rôle peut être assigné manuellement à un utilisateur
 * @param permissions  liste des permissions accordées par ce rôle
 * @param createdAt    date et heure de création du rôle
 */
public record RoleResponseDto(
        UUID id,
        String name,
        String slug,
        String description,
        Boolean isAssignable,
        List<PermissionResponseDto> permissions,
        String createdByName,
        LocalDateTime createdAt
) {}
