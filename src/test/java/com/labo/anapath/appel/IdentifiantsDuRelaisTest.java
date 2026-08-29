package com.labo.anapath.appel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les identifiants éphémères du relais.
 *
 * <h2>Pourquoi un vecteur figé</h2>
 *
 * <p>Le couple ci-dessous a été présenté à un vrai coturn 4.6, configuré avec le
 * fichier {@code relais/turnserver.conf} de ce dépôt : l'allocation a été
 * acceptée, et le même couple avec un mot de passe faux ou un horodatage périmé
 * a été refusé. Ce test relie donc le code Java à un comportement mesuré, et non
 * à une lecture de la spécification.</p>
 *
 * <p>Il y a trois façons de se tromper ici — SHA-256 au lieu de SHA-1,
 * hexadécimal au lieu de base64, nom d'utilisateur sans l'horodatage — et toutes
 * donnent le même symptôme : un 401 muet, au moment précis où l'appel devait
 * s'établir. Aucune ne se voit à la lecture.</p>
 */
class IdentifiantsDuRelaisTest {

    private static final String SECRET = "secret-de-test-0123456789";
    private static final String UTILISATEUR = "1788109552:anapath";
    private static final String ATTENDU = "gKyIn5aX3VEnt0l0d9netxi23CE=";

    @Test
    @DisplayName("le mot de passe est celui qu'un vrai coturn accepte")
    void vecteurEprouveContreCoturn() {
        assertThat(AppelController.signer(UTILISATEUR, SECRET)).isEqualTo(ATTENDU);
    }

    @Test
    @DisplayName("un secret différent donne un mot de passe différent")
    void leSecretCompte() {
        assertThat(AppelController.signer(UTILISATEUR, "un-autre-secret"))
                .isNotEqualTo(ATTENDU);
    }

    @Test
    @DisplayName("l'horodatage fait partie de la signature")
    void lHorodatageEstSigne() {
        // Sans cela, un identifiant recopié depuis un téléphone servirait pour
        // toujours : c'est l'expiration qui fait tout l'intérêt du procédé.
        assertThat(AppelController.signer("1788109999:anapath", SECRET))
                .isNotEqualTo(ATTENDU);
    }

    @Test
    @DisplayName("un secret absent ne fabrique pas un mot de passe vide")
    void secretVide() {
        // Un mot de passe vide serait accepté par personne, mais donnerait un
        // 401 là où l'application doit plutôt dire « relais non configuré ».
        assertThat(AppelController.signer(UTILISATEUR, "")).isNull();
    }
}
