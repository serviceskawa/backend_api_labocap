package com.labo.anapath.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux fenêtres de session, et pourquoi elles ne peuvent pas être la même.
 *
 * <p>Une session web s'éteint après quinze minutes sans activité : un poste du
 * comptoir laissé ouvert ne doit pas rester utilisable. Un téléphone enrôlé ne
 * suit pas cette règle — il se verrouille de lui-même, et l'application
 * redemande son code PIN selon le métier, chaque heure au laboratoire et deux
 * fois par jour au comptoir. Lui appliquer les quinze minutes du web
 * réclamerait un code quatre fois par heure.</p>
 *
 * <p>Les deux durées se distinguent à la revendication d'appareil. Rien à
 * l'écran ne dirait qu'elles se sont confondues : on s'en apercevrait par des
 * agents qui redemandent leur PIN sans cesse, plusieurs jours plus tard.</p>
 */
class DureeDesSessionsTest {

    private static final long QUINZE_MINUTES = 900_000L;
    private static final long CINQ_MINUTES = 300_000L;
    private static final long VINGT_QUATRE_HEURES = 86_400_000L;
    private static final long SEPT_JOURS = 604_800_000L;

    private JwtTokenProvider fournisseur() {
        JwtProperties proprietes = new JwtProperties();
        proprietes.setSecret("une-cle-de-test-suffisamment-longue-pour-hmac-sha256!!");
        proprietes.setExpirationMs(CINQ_MINUTES);
        proprietes.setRefreshExpirationMs(QUINZE_MINUTES);
        proprietes.setMobileExpirationMs(VINGT_QUATRE_HEURES);
        proprietes.setMobileRefreshExpirationMs(SEPT_JOURS);
        return new JwtTokenProvider(proprietes);
    }

    private UserPrincipal quelquun() {
        return new UserPrincipal(UUID.randomUUID(), "agent@test.bj", "x",
                UUID.randomUUID(), true, List.of());
    }

    /** Durée de vie d'un jeton, à la seconde près. */
    private long secondes(JwtTokenProvider f, String jeton) {
        return Duration.between(Instant.now(), f.extractExpiry(jeton)).toSeconds();
    }

    @Test
    @DisplayName("un jeton web vit cinq minutes, un jeton d'appareil vingt-quatre heures")
    void lesDeuxAccesDiffèrent() {
        JwtTokenProvider f = fournisseur();

        long web = secondes(f, f.generateToken(quelquun(), null));
        long appareil = secondes(f, f.generateToken(quelquun(), UUID.randomUUID()));

        assertThat(web).isBetween(295L, 300L);
        assertThat(appareil).isBetween(86_390L, 86_400L);
    }

    @Test
    @DisplayName("la fenêtre web est de quinze minutes, celle d'un appareil de sept jours")
    void lesDeuxFenetresDiffèrent() {
        JwtTokenProvider f = fournisseur();
        UUID utilisateur = UUID.randomUUID();

        long web = secondes(f, f.generateRefreshToken(utilisateur));
        long appareil = secondes(f, f.generateRefreshToken(utilisateur, true));

        // C'est cette fenêtre-là qui décide de la durée d'une session : le
        // navigateur renouvelle seul le jeton d'accès tant qu'elle est ouverte.
        assertThat(web).isBetween(895L, 900L);
        assertThat(appareil).isBetween(604_790L, 604_800L);
    }

    @Test
    @DisplayName("le jeton d'accès web est bien plus court que la fenêtre")
    void lAccesEstPlusCourtQueLaFenetre() {
        JwtTokenProvider f = fournisseur();

        long acces = secondes(f, f.generateToken(quelquun(), null));
        long fenetre = secondes(f, f.generateRefreshToken(UUID.randomUUID()));

        // C'est l'expiration du jeton d'accès qui déclenche le renouvellement,
        // donc qui fait glisser la fenêtre sur l'activité réelle. Les deux
        // réglés à la même valeur, la fenêtre expirerait à l'instant précis où
        // l'on tente de la prolonger.
        assertThat(acces).isLessThan(fenetre / 2);
    }
}
