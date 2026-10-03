package com.labo.anapath.testorder;

import java.util.UUID;
import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * Une pièce jointe d'une demande d'examen.
 *
 * @param index    rang dans la liste, tel que la suppression l'attend
 * @param filename nom de stockage, aléatoire
 * @param url      adresse de lecture
 * @param addedAt  date d'ajout, nulle pour les images antérieures à son
 *                 enregistrement — leur date n'existe nulle part et on préfère
 *                 ne rien afficher plutôt que d'en inventer une
 */
public record ImageDto(int index, String filename, String url, LocalDateTime addedAt) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(filename);
    }
}
