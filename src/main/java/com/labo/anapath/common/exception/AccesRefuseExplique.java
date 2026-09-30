package com.labo.anapath.common.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Un refus d'accès dont le motif peut être montré à la personne.
 *
 * <p>Par défaut, {@code GlobalExceptionHandler} remplace le message de tout
 * {@link AccessDeniedException} par un texte générique. C'est la bonne règle :
 * détailler pourquoi un accès est refusé revient souvent à décrire ce qui
 * existe derrière, et renseigne autant celui qui cherche à entrer que celui qui
 * s'est trompé de bouton.</p>
 *
 * <p>Quelques refus échappent à cette prudence, parce que leur motif ne révèle
 * rien que la personne ne sache déjà et qu'il lui dit quoi faire. « Vous ne
 * validez pas les comptes-rendus de type Biopsie ; ceux qui vous sont confiés
 * sont Cytologie et Immuno Externe » n'apprend rien à un intrus — il ne parle
 * que du périmètre de celui qui lit — et épargne à l'agent un appel au support
 * pour découvrir que cette demande-là n'était pas pour lui.</p>
 *
 * <p>À réserver à ces cas. Tout refus dont le motif décrit l'état d'une
 * ressource, l'existence d'un objet ou les droits d'un tiers reste un
 * {@link AccessDeniedException} nu.</p>
 */
public class AccesRefuseExplique extends AccessDeniedException {

    public AccesRefuseExplique(String message) {
        super(message);
    }
}
