package com.labo.anapath.report;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une modification apportée à un compte-rendu après sa signature.
 *
 * <p>Alimente le bandeau qui met ces modifications en exergue sur l'écran du
 * compte-rendu. Porte le strict nécessaire : qui, quand, quoi.</p>
 *
 * @param auteurId  identifiant de l'auteur, {@code null} si le compte a depuis
 *                  été supprimé — le nom, lui, reste lisible dans {@code auteur}
 * @param auteur    nom complet de l'auteur au moment de l'affichage
 * @param date      horodatage de la modification
 * @param champs    champs touchés, déjà mis en forme pour l'affichage
 */
public record ModificationApresSignatureDto(
        UUID auteurId,
        String auteur,
        LocalDateTime date,
        String champs) {
}
