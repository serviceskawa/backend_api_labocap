package com.labo.anapath.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    private final Totp totp = new Totp();

    /** Vecteur de la RFC 6238 (secret ASCII « 12345678901234567890 », t = 59 s → 94287082, six derniers chiffres). */
    @Test
    void vecteurDeLaRfc6238() {
        String secret = Totp.base32("12345678901234567890".getBytes());
        assertThat(secret).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(totp.code(secret, 59_000L)).isEqualTo(287082);
        assertThat(totp.code(secret, 1_111_111_109_000L)).isEqualTo(81804);  // RFC : 07081804
        assertThat(totp.correspondAuPas(secret, 287082, 59_000L)).isTrue();
        assertThat(totp.correspondAuPas(secret, 287082, 60_000L)).isFalse();
    }

    @Test
    void unSecretNeufSeVerifieEtLeBase32FaitLAllerRetour() {
        String secret = totp.nouveauSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(totp.verifier(secret, totp.code(secret, System.currentTimeMillis()))).isTrue();
        assertThat(totp.verifier(secret, 123456)).isFalse();
        assertThat(Totp.base32(Totp.decoderBase32("JBSWY3DPEHPK3PXP"))).isEqualTo("JBSWY3DPEHPK3PXP");
    }
}
