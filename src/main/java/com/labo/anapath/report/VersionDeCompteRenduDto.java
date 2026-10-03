package com.labo.anapath.report;

import java.time.LocalDateTime;

/**
 * Une version antérieure d'un compte-rendu, telle qu'elle était avant d'être
 * écrasée.
 *
 * @param savedBy nom de l'auteur de la modification qui a provoqué la prise
 *                de version, ou « Utilisateur supprimé »
 */
public record VersionDeCompteRenduDto(
        int version,
        LocalDateTime savedAt,
        String savedBy,
        String status,
        String title,
        String signataires,
        String content,
        String contentMicro,
        String comment,
        String commentSup,
        String descriptionSupplementaire,
        String descriptionSupplementaireMicro) {

    /** Entrée de la liste : de quoi choisir une version sans en charger les textes. */
    public record Resume(int version, LocalDateTime savedAt, String savedBy, String status) {
    }
}
