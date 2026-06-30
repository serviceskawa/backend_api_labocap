package com.labo.anapath.doc;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Corps de requête pour le partage d'un document avec un rôle. */
public record DocShareRequestDto(
        @NotNull(message = "Le rôle est obligatoire") UUID roleId
) {}
