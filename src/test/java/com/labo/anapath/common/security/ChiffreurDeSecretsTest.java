package com.labo.anapath.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le chiffreur des secrets courts.
 *
 * <p>Ce qu'on vérifie ici n'est pas la cryptographie — AES-GCM n'a pas besoin
 * de nous — mais les quatre comportements dont dépend l'écran : le tour
 * complet, le fait que deux chiffrés d'un même secret diffèrent, le silence
 * sans clé, et le refus d'une valeur scellée par une autre clé.</p>
 */
class ChiffreurDeSecretsTest {

    private static String cle(byte remplissage) {
        byte[] octets = new byte[32];
        java.util.Arrays.fill(octets, remplissage);
        return Base64.getEncoder().encodeToString(octets);
    }

    @Test
    @DisplayName("un secret chiffré se relit à l'identique")
    void tourComplet() {
        ChiffreurDeSecrets c = new ChiffreurDeSecrets(cle((byte) 7), "");

        String scelle = c.chiffrer("K7M2-9PQR");

        assertThat(scelle).isNotNull().startsWith("v1:").doesNotContain("K7M2");
        assertThat(c.dechiffrer(scelle)).isEqualTo("K7M2-9PQR");
    }

    @Test
    @DisplayName("deux chiffrés du même secret diffèrent")
    void deuxChiffresDifferent() {
        ChiffreurDeSecrets c = new ChiffreurDeSecrets(cle((byte) 7), "");

        // Un IV tiré à chaque fois. Sans cela, deux agents portant le même code
        // se reconnaîtraient à l'œil dans un export de la base.
        assertThat(c.chiffrer("ABCD")).isNotEqualTo(c.chiffrer("ABCD"));
    }

    @Test
    @DisplayName("sans clé, rien n'est conservé de relisible")
    void sansCle() {
        ChiffreurDeSecrets c = new ChiffreurDeSecrets("", "");

        // Nul, et non une exception : une installation qui n'a jamais demandé
        // cette fonction doit continuer de délivrer des accès.
        assertThat(c.estActif()).isFalse();
        assertThat(c.chiffrer("ABCD")).isNull();
        assertThat(c.dechiffrer("v1:peu importe")).isNull();
    }

    @Test
    @DisplayName("une valeur scellée par une autre clé ne se relit pas")
    void cleChangee() {
        String scelle = new ChiffreurDeSecrets(cle((byte) 7), "").chiffrer("ABCD");

        // « Non réaffichable », pas un écran en erreur.
        assertThat(new ChiffreurDeSecrets(cle((byte) 9), "").dechiffrer(scelle)).isNull();
    }

    @Test
    @DisplayName("la clé des fichiers sert de second recours")
    void repliSurLaCleDesFichiers() {
        // Pour ne pas imposer une seconde clé à un laboratoire qui en a déjà
        // posé une pour le chiffrement des fichiers.
        ChiffreurDeSecrets c = new ChiffreurDeSecrets("", cle((byte) 3));

        assertThat(c.estActif()).isTrue();
        assertThat(c.dechiffrer(c.chiffrer("ABCD"))).isEqualTo("ABCD");
    }

    @Test
    @DisplayName("une clé de mauvaise taille est ignorée plutôt que fatale")
    void cleInvalide() {
        ChiffreurDeSecrets c = new ChiffreurDeSecrets("cGFzIHVuZSBjbMOp", "");

        assertThat(c.estActif()).isFalse();
    }
}
