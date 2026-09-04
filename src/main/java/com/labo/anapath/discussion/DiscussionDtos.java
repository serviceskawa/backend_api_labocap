package com.labo.anapath.discussion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Ce que l'écran de discussion reçoit et envoie. */
public final class DiscussionDtos {

    private DiscussionDtos() {}

    /**
     * Le fil complet d'un dossier.
     *
     * @param testOrderCode ce que le médecin lit en tête d'écran
     * @param participants  qui peut être tagué, et à quel titre
     */
    public record FilDto(
            UUID id,
            UUID testOrderId,
            String testOrderCode,
            List<ParticipantDto> participants,
            List<MessageDto> messages) {
    }

    /**
     * @param nom  nom puis prénoms, comme partout
     * @param role « medecin » ou « technicien », figé à l'entrée dans le fil
     */
    public record ParticipantDto(UUID userId, String nom, String role) {
    }

    /**
     * @param auteurNom     qui parle — le nom, pas seulement un identifiant
     * @param auteurRole    sous quelle casquette il parlait ce jour-là
     * @param taggedUserId  le destinataire nommément visé, ou nul pour le groupe
     * @param lu            cette personne-ci l'a-t-elle déjà lu ?
     */
    public record MessageDto(
            UUID id,
            UUID auteurId,
            String auteurNom,
            String auteurRole,
            String type,
            String contenu,
            UUID taggedUserId,
            String taggedNom,
            LocalDateTime createdAt,
            boolean lu) {
    }

    /** Un message à poster. */
    public record NouveauMessage(String type, String contenu, UUID taggedUserId) {
    }

    /** Combien de messages non lus, pour un dossier. */
    public record NonLusDto(UUID testOrderId, long nombre) {
    }
}
