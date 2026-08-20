package com.labo.anapath.user;

import java.util.UUID;

/**
 * Un signataire possible d'un compte rendu, réduit au strict nécessaire.
 *
 * <p>Ni courriel, ni téléphone, ni rôles : ce que demande un menu déroulant, et
 * rien de plus. C'est ce qui permet d'ouvrir cette liste aux médecins sans leur
 * accorder {@code edit-users}, qui donnerait le droit de créer, modifier et
 * supprimer des comptes — un droit d'administration pour remplir un menu.</p>
 *
 * @param actif un signataire dont le compte est désactivé reste listé : trois
 *              des cinq docteurs le sont et ont signé 9 278 comptes rendus. Les
 *              écarter viderait le champ sur tous ces dossiers. L'appelant
 *              signale l'état plutôt que de masquer la personne.
 */
public record SignataireDto(UUID id, String nom, boolean actif) {

    static SignataireDto de(User u) {
        return new SignataireDto(
                u.getId(),
                com.labo.anapath.common.NomComplet.de(u.getLastname(), u.getFirstname()),
                u.isActive());
    }
}
