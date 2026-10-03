package com.labo.anapath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot 4 a découpé ses auto-configurations en modules : une
 * bibliothèque présente ne suffit plus, il faut aussi le module qui la
 * branche. Les tests d'intégration éteignent Flyway (Hibernate crée le schéma)
 * et n'auraient rien vu : en production, le 03/10/2026, les migrations ne
 * jouaient plus et l'API a refusé de démarrer. Ce test fixe la liste.
 */
class ModulesDeBootPresentsTest {

    @Test
    @DisplayName("les auto-configurations dont la production dépend sont sur le classpath")
    void autoConfigurationsPresentes() {
        for (String classe : new String[] {
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration",
                "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.web.servlet.SecurityAutoConfiguration",
                "org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration",
        }) {
            assertThat(estSurLeClasspath(classe)).as(classe).isTrue();
        }
    }

    private static boolean estSurLeClasspath(String nom) {
        try {
            Class.forName(nom, false, ModulesDeBootPresentsTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
