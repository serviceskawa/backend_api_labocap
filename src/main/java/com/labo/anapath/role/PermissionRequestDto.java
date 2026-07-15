package com.labo.anapath.role;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour la création et la mise à jour d'une permission.
 *
 * <p>Une permission est identifiée par un {@code slug} unique suivant la
 * convention {@code {operation}-{ressource}} (ex. : {@code view-patients}),
 * et porte un {@code name} lisible affiché dans les interfaces.</p>
 */
@Getter
@Setter
public class PermissionRequestDto {

    /** Libellé lisible de la permission (obligatoire, ex. : "Voir les patients"). */
    @NotBlank(message = "Le nom de la permission est obligatoire")
    private String name;

    /**
     * Slug technique unique (obligatoire, ex. : {@code view-patients}).
     * Utilisé dans les vérifications Spring Security.
     */
    @NotBlank(message = "Le slug de la permission est obligatoire")
    private String slug;
}
