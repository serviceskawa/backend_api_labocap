package com.labo.anapath.mobile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La règle de vie d'un code d'enrôlement.
 *
 * <p>Elle a changé de nature : le code s'éteignait au premier appareil enrôlé,
 * il s'éteint désormais quand on le révoque. Ces quatre cas sont ce qui
 * distingue les deux comportements, et ce qu'une régression rendrait
 * silencieux — un code qui redevient à usage unique ne casse rien, il oblige
 * seulement l'agent d'à côté à redemander un accès.</p>
 */
class CodeEnrolementReutilisableTest {

    private MobileEnrollmentCode code(LocalDateTime expiration) {
        MobileEnrollmentCode c = new MobileEnrollmentCode(
                UUID.randomUUID(), "$2a$10$empreinte", expiration, UUID.randomUUID());
        try {
            Field id = MobileEnrollmentCode.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(c, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return c;
    }

    @Test
    @DisplayName("un code déjà employé reste utilisable")
    void resteUtilisableApresUsage() {
        MobileEnrollmentCode c = code(null);

        c.noterUnUsage(UUID.randomUUID());

        // Le cas qui motive tout : un deuxième téléphone, ou une réinstallation
        // après un échec, doit passer avec le même QR.
        assertThat(c.estUtilisable()).isTrue();
        assertThat(c.getUsedCount()).isEqualTo(1);
        assertThat(c.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("le premier usage garde sa date quand un second survient")
    void lePremierUsageNeBougePlus() {
        MobileEnrollmentCode c = code(null);
        c.noterUnUsage(UUID.randomUUID());
        LocalDateTime premier = c.getUsedAt();
        UUID second = UUID.randomUUID();

        c.noterUnUsage(second);

        // `usedAt` dit depuis quand le code circule ; l'écraser à chaque
        // enrôlement ferait passer un code ancien pour un code tout neuf.
        assertThat(c.getUsedAt()).isEqualTo(premier);
        assertThat(c.getUsedCount()).isEqualTo(2);
        assertThat(c.getDeviceId()).isEqualTo(second);
    }

    @Test
    @DisplayName("un code révoqué cesse d'enrôler, même jamais employé")
    void laRevocationEteint() {
        MobileEnrollmentCode c = code(null);

        c.revoquer(UUID.randomUUID());

        assertThat(c.estUtilisable()).isFalse();
        assertThat(c.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("un code hérité garde son échéance")
    void lEcheanceHeriteeVautEncore() {
        // Les codes délivrés avant le changement portent une expiration. Elle
        // continue de valoir : les ignorer ranimerait des codes périmés.
        assertThat(code(LocalDateTime.now().minusHours(1)).estUtilisable()).isFalse();
        assertThat(code(LocalDateTime.now().plusHours(1)).estUtilisable()).isTrue();
    }
}
