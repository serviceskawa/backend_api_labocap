package com.labo.anapath.common.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecretChiffreConverterTest {

    private static final String CLE = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void avecCleLaBaseNeVoitPlusLeSecretEtLeRelit() {
        SecretChiffreConverter c = new SecretChiffreConverter(new ChiffreurDeSecrets(CLE, ""));
        String enBase = c.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");
        assertThat(enBase).startsWith("v1:").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(c.convertToEntityAttribute(enBase)).isEqualTo("JBSWY3DPEHPK3PXP");
        // Une valeur ancienne, en clair, se lit encore.
        assertThat(c.convertToEntityAttribute("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void sansCleRienNeChange() {
        SecretChiffreConverter c = new SecretChiffreConverter(new ChiffreurDeSecrets("", ""));
        assertThat(c.convertToDatabaseColumn("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
    }
}
