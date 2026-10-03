package com.labo.anapath.mobile;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MobileEnrollmentCodeTest {

    @Test
    void unCodeSertUneFoisEtQuinzeMinutes() {
        MobileEnrollmentCode code = new MobileEnrollmentCode(UUID.randomUUID(), "hash",
                LocalDateTime.now().plusMinutes(15), UUID.randomUUID());
        assertThat(code.estUtilisable()).isTrue();
        code.noterUnUsage(UUID.randomUUID());
        assertThat(code.estUtilisable()).as("déjà utilisé").isFalse();

        MobileEnrollmentCode perime = new MobileEnrollmentCode(UUID.randomUUID(), "hash",
                LocalDateTime.now().minusSeconds(1), UUID.randomUUID());
        assertThat(perime.estUtilisable()).as("expiré").isFalse();
    }
}
