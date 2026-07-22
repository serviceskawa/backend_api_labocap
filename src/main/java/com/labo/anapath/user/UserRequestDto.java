package com.labo.anapath.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO de requête pour la création et la mise à jour d'un utilisateur.
 *
 * <p>Le mot de passe est obligatoire uniquement à la création ; lors d'une mise
 * à jour, sa modification passe par l'endpoint dédié {@code PATCH /{id}/password}.</p>
 */
@Getter
@Setter
public class UserRequestDto {

    /** Prénom de l'utilisateur (obligatoire). */
    @NotBlank(message = "Le prénom est obligatoire")
    private String firstname;

    /** Nom de famille de l'utilisateur (obligatoire). */
    @NotBlank(message = "Le nom est obligatoire")
    private String lastname;

    /** Adresse e-mail valide servant d'identifiant de connexion (obligatoire). */
    @Email(message = "L'email doit être valide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    /**
     * Mot de passe en clair (8 caractères minimum).
     * Sera haché en BCrypt avant persistance.
     */
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    /** Numéro de téléphone (optionnel). Champ « Telephone » côté Laravel. */
    private String phone;

    /** Numéro WhatsApp (optionnel). */
    private String whatsapp;

    /** Taux de commission en pourcentage (optionnel). */
    private BigDecimal commission;

    /** Signature du praticien (optionnel, data-URL base64). */
    private String signature;

    /** Statut actif du compte. Si {@code null} à la création, le compte est actif par défaut. */
    private Boolean isActive;

    /** Liste des identifiants de rôles à assigner à l'utilisateur. */
    private List<UUID> roleIds = new ArrayList<>();

    /** Liste des identifiants de branches à affecter à l'utilisateur (pivot branch_user). */
    private List<UUID> branchIds = new ArrayList<>();
}
