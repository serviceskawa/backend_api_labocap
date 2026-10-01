package com.labo.anapath.testorder;

import java.util.UUID;

/**
 * Une demande d'examen réduite à ce qu'il faut pour la reconnaître.
 *
 * <p>Destinée à l'application mobile, qui en garde la liste pour résoudre un
 * code scanné sans réseau. Le technicien scanne un tube au laboratoire, souvent
 * dans une pièce mal couverte : sans cet index, il ne peut pas même savoir de
 * quelle demande il s'agit, et tout le travail hors ligne s'arrête là.</p>
 *
 * <p>Cinq champs, et pas un de plus. Le DTO complet d'une demande pèse
 * 1 375 octets — mesuré sur la base de production — soit 4,1 Mo pour les
 * 3 133 demandes du jeu de travail. Cet index en pèse une centaine, soit
 * environ 370 Ko : onze fois moins, sur le lien même qu'on cherche à ménager.
 * Y ajouter un champ « parce qu'il pourrait servir » annulerait l'intérêt.</p>
 */
public record EntreeDIndexDto(
        UUID id,
        String code,
        String patient,
        TestOrderStatus statut) {
}
