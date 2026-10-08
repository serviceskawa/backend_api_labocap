package com.labo.anapath.testorder;

import com.labo.anapath.discussion.DiscussionDtos.FilDto;

import java.util.List;
import java.util.UUID;

/**
 * Tout ce qu'un dossier demande à l'ouverture, rassemblé en une réponse.
 *
 * <h2>Pourquoi grouper</h2>
 *
 * <p>L'application descend d'avance les lots confiés au laboratoire pour qu'ils
 * s'ouvrent sans réseau. Les lots seuls ne suffisent pas : toucher un dossier
 * demande ses images, l'historique de son patient et son fil de discussion,
 * soit trois appels. Pour les 991 demandes des soixante lots préchargés, cela
 * faisait 2 973 requêtes — une demi-heure sur un lien de brousse, et autant
 * d'occasions d'échouer.</p>
 *
 * <p>Groupées par cinquante, elles tiennent en une vingtaine d'appels.</p>
 *
 * <h2>Ce que le fil vaut ici</h2>
 *
 * <p>Il est LU, jamais créé. L'ouverture ordinaire d'une discussion crée le fil
 * s'il n'existe pas, ce qui est juste quand quelqu'un vient y écrire ; le faire
 * en préchargement ouvrirait neuf cents fils vides que personne n'a demandés.
 * Une demande sans fil rend donc un fil sans identifiant et sans message —
 * ce qui est la vérité, et ce que l'écran doit montrer.</p>
 */
public record DossierHorsLigneDto(
        UUID id,
        /** Le code de la demande : l'application garde la fiche sous sa clé. */
        String code,
        com.labo.anapath.report.DossierResumeDto fiche,
        List<ImageDto> images,
        List<HistoriquePatientDto> historique,
        FilDto fil) {
}
