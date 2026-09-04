package com.labo.anapath.testorder;

/**
 * Combien de dossiers la file compte, dans chacun des trois états.
 *
 * <p>Calculé sur le périmètre courant — année, lot, urgence — mais sans le
 * filtre de statut lui-même : c'est la répartition de ce périmètre qu'on
 * affiche, et l'y appliquer mettrait deux compteurs sur trois à zéro dès qu'on
 * se pose sur le premier.</p>
 *
 * <p>Rendu par le serveur depuis que la file est paginée. Le téléphone les
 * comptait sur ce qu'il avait reçu ; il ne reçoit plus qu'une page, et un
 * compteur qui ne parle que de la page dirait « 20 à traiter » à un médecin qui
 * en a neuf cents.</p>
 */
public record ResumeFileDto(
        long aTraiter,
        long prisEnCharge,
        long termine,

        /**
         * Les dossiers marqués urgents du périmètre, et ceux en retard.
         *
         * <p>Joints à la répartition plutôt que demandés à part : l'accueil du
         * médecin affiche les cinq chiffres ensemble, et les chercher en cinq
         * requêtes ferait cinq allers-retours pour un écran qu'on ouvre à
         * chaque fois qu'on sort le téléphone.</p>
         *
         * <p>Comptés sur le périmètre en forçant leur propre critère : ce sont
         * les urgents <em>de ce périmètre</em>, non ceux du filtre courant.</p>
         */
        long urgents,
        long enRetard,

        /**
         * Le même périmètre, rangé par état de la <em>demande</em>.
         *
         * <p>Autre chose que les trois premiers, qui disent où en est le
         * médecin. Les deux rangements coexistent à l'écran — l'accueil montre
         * le circuit du bon, la file montre le travail — et les confondre
         * ferait dire « remise » d'un dossier que le médecin n'a pas ouvert.</p>
         */
        long enAttente,
        long validees,
        long remises) {}
