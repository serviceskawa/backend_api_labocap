package com.labo.anapath.common.supervision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.labo.anapath.common.supervision.CompteurDAlertes.Type.ECHEC_DE_CONNEXION;
import static com.labo.anapath.common.supervision.CompteurDAlertes.Type.ERREUR_SERVEUR;
import static org.assertj.core.api.Assertions.assertThat;

class CompteurDAlertesTest {

    private final CompteurDAlertes compteur = new CompteurDAlertes();

    @Test
    @DisplayName("la fenêtre glisse : le maximum est pris sur la pire position")
    void fenetreGlissante() {
        // 3 par minute de la minute 0 à 9, puis une rafale de 15 à la minute 30.
        for (long m = 0; m < 10; m++) {
            for (int i = 0; i < 3; i++) compteur.compter(ECHEC_DE_CONNEXION, m);
        }
        for (int i = 0; i < 15; i++) compteur.compter(ECHEC_DE_CONNEXION, 30);

        assertThat(compteur.maximumSur(ECHEC_DE_CONNEXION, Duration.ofMinutes(10))).isEqualTo(30);
        assertThat(compteur.maximumSur(ECHEC_DE_CONNEXION, Duration.ofMinutes(5))).isEqualTo(15);
        assertThat(compteur.maximumSur(ECHEC_DE_CONNEXION, Duration.ofMinutes(1))).isEqualTo(15);
        assertThat(compteur.maximumSur(ERREUR_SERVEUR, Duration.ofMinutes(5))).isZero();
    }

    @Test
    @DisplayName("les événements du moment comptent, et l'oubli ne touche que les vieilles minutes")
    void maintenantEtOubli() {
        compteur.echecDeConnexion();
        compteur.echecDeConnexion();
        compteur.compter(ECHEC_DE_CONNEXION, 0); // il y a cinquante ans

        compteur.oublierLesVieillesMinutes();

        assertThat(compteur.maximumSur(ECHEC_DE_CONNEXION, Duration.ofMinutes(10))).isEqualTo(2);
    }
}
