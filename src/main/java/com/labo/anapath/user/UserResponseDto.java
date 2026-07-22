package com.labo.anapath.user;

import com.labo.anapath.branch.BranchResponseDto;
import com.labo.anapath.role.PermissionResponseDto;
import com.labo.anapath.role.RoleResponseDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO de réponse représentant un utilisateur exposé par l'API.
 *
 * <p>Les informations sensibles (mot de passe, secret 2FA, OTP) sont
 * intentionnellement absentes de ce record.</p>
 *
 * @param id        identifiant unique de l'utilisateur
 * @param firstname prénom
 * @param lastname  nom de famille
 * @param email     adresse e-mail
 * @param phone     numéro de téléphone
 * @param isActive  indique si le compte est actif
 * @param branchId  identifiant de la succursale de rattachement
 * @param createdAt date et heure de création du compte
 * @param roles             liste des rôles attribués à l'utilisateur
 * @param signature         signature numérique du praticien (base64 ou texte)
 * @param directPermissions permissions directement assignées à l'utilisateur (sans rôle)
 */
public record UserResponseDto(
        UUID id,
        String firstname,
        String lastname,
        String email,
        String phone,
        String whatsapp,
        BigDecimal commission,
        boolean isActive,
        UUID branchId,
        LocalDateTime createdAt,
        List<RoleResponseDto> roles,
        List<BranchResponseDto> branches,
        String signature,
        List<PermissionResponseDto> directPermissions
) {}
