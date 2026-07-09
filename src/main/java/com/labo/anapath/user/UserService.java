package com.labo.anapath.user;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.role.PermissionResponseDto;

import java.util.List;
import java.util.UUID;

/**
 * Interface de service métier pour la gestion des utilisateurs.
 *
 * <p>Définit les opérations disponibles sur les comptes utilisateurs :
 * consultation, création, mise à jour, suppression, gestion du statut
 * et changement de mot de passe.</p>
 */
public interface UserService {

    /**
     * Retourne la liste paginée des utilisateurs d'une succursale.
     *
     * @param page     numéro de page (0-indexé)
     * @param size     taille de la page
     * @param branchId identifiant de la succursale
     * @return page de DTOs utilisateurs
     */
    PageResponse<UserResponseDto> findAll(int page, int size, UUID branchId);

    /**
     * Recherche un utilisateur par son identifiant, filtré par succursale.
     *
     * @param id       identifiant UUID de l'utilisateur
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     * @return le DTO de l'utilisateur trouvé
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si l'utilisateur n'existe pas ou n'appartient pas à la succursale
     */
    UserResponseDto findById(UUID id, UUID branchId);

    /**
     * Crée un nouvel utilisateur dans la succursale spécifiée.
     *
     * @param dto      données de création
     * @param branchId identifiant de la succursale de rattachement
     * @return le DTO de l'utilisateur créé
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si l'e-mail est déjà utilisé
     */
    UserResponseDto create(UserRequestDto dto, UUID branchId);

    /**
     * Met à jour les informations d'un utilisateur existant, vérifié dans la succursale de l'appelant.
     *
     * @param id       identifiant UUID de l'utilisateur
     * @param dto      nouvelles données
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     * @return le DTO mis à jour
     */
    UserResponseDto update(UUID id, UserRequestDto dto, UUID branchId);

    /**
     * Supprime logiquement un utilisateur (soft-delete), vérifié dans la succursale de l'appelant.
     *
     * @param id       identifiant UUID de l'utilisateur à supprimer
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     */
    void delete(UUID id, UUID branchId);

    /**
     * Change le mot de passe d'un utilisateur après vérification de l'ancien.
     *
     * @param id       identifiant UUID de l'utilisateur
     * @param request  objet contenant l'ancien et le nouveau mot de passe
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     * @throws com.labo.anapath.common.exception.BusinessException si l'ancien mot de passe est incorrect
     */
    void updatePassword(UUID id, UpdatePasswordRequest request, UUID branchId);

    /**
     * Met à jour les informations personnelles de l'utilisateur lui-même.
     *
     * <p>Ne touche ni aux rôles, ni au statut, ni à l'e-mail : ces champs ne sont
     * pas modifiables par l'utilisateur depuis son profil.</p>
     *
     * @param id       identifiant UUID de l'utilisateur courant
     * @param request  nouvelles informations personnelles
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     * @return le DTO mis à jour
     */
    UserResponseDto updateMyProfile(UUID id, UpdateProfileRequest request, UUID branchId);

    /**
     * Change l'adresse e-mail de connexion de l'utilisateur lui-même.
     *
     * @param id       identifiant UUID de l'utilisateur courant
     * @param request  nouvelle adresse et mot de passe actuel de confirmation
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     * @return le DTO mis à jour
     * @throws com.labo.anapath.common.exception.BusinessException          si le mot de passe est incorrect
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si l'adresse est déjà prise
     */
    UserResponseDto updateMyEmail(UUID id, UpdateEmailRequest request, UUID branchId);

    /**
     * Bascule le statut actif/inactif d'un utilisateur, vérifié dans la succursale de l'appelant.
     * En cas de désactivation, la session et le 2FA sont également réinitialisés.
     *
     * @param id       identifiant UUID de l'utilisateur
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     */
    void toggleStatus(UUID id, UUID branchId);

    /**
     * Retourne les permissions directement assignées à un utilisateur.
     *
     * @param userId   identifiant UUID de l'utilisateur
     * @param branchId identifiant de la succursale (isolation multi-tenant)
     * @return liste des DTOs de permissions directes
     */
    List<PermissionResponseDto> getUserPermissions(UUID userId, UUID branchId);

    /**
     * Remplace toutes les permissions directes d'un utilisateur.
     *
     * @param userId        identifiant UUID de l'utilisateur
     * @param permissionIds liste des UUIDs de permissions à assigner (remplace l'existante)
     * @param branchId      identifiant de la succursale (isolation multi-tenant)
     */
    void setUserPermissions(UUID userId, List<UUID> permissionIds, UUID branchId);
}
