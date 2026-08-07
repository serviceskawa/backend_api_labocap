package com.labo.anapath.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le chiffrement ne doit exister QUE s'il est demandé.
 *
 * <p>C'est ce qui rend cette étape livrable sans risque : tant que
 * {@code app.storage.encryption.enabled} n'est pas posé, ajouter le composant au
 * projet ne change rien — ni au démarrage, ni aux fichiers. Sans cette garantie,
 * la fusionner exigerait de poser une clé sur tous les environnements le jour
 * même.</p>
 *
 * <p>Le contexte d'essai enregistre la VRAIE classe, annotation comprise : une
 * première version déclarait un bean écrit à la main, qui ne portait pas la
 * condition — elle vérifiait donc le constructeur en croyant vérifier
 * l'activation.</p>
 */
class FileCipherActivationTest {

    private static final String CLE = Base64.getEncoder().encodeToString(new byte[32]);

    private final ApplicationContextRunner contexte =
            new ApplicationContextRunner().withUserConfiguration(FileCipher.class);

    @Test
    @DisplayName("Sans le drapeau, aucun chiffreur n'est créé et le contexte démarre")
    void absentParDefaut() {
        contexte.run(ctx -> assertThat(ctx)
                .hasNotFailed()
                .doesNotHaveBean(FileCipher.class));
    }

    @Test
    @DisplayName("Drapeau à faux : toujours aucun chiffreur")
    void absentSiDrapeauFaux() {
        contexte.withPropertyValues("app.storage.encryption.enabled=false")
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .doesNotHaveBean(FileCipher.class));
    }

    @Test
    @DisplayName("Avec le drapeau et une clé, le chiffreur existe")
    void presentSiActive() {
        contexte.withPropertyValues(
                        "app.storage.encryption.enabled=true",
                        "app.storage.encryption.key=" + CLE)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(FileCipher.class));
    }

    @Test
    @DisplayName("Avec le drapeau mais sans clé, le contexte refuse de démarrer")
    void echoueSiActiveSansCle() {
        contexte.withPropertyValues("app.storage.encryption.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
