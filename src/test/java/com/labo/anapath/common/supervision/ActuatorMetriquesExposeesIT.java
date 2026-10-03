package com.labo.anapath.common.supervision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avec {@code MANAGEMENT_METRICS_EXPOSED=true} : les métriques répondent depuis
 * la boucle locale, et restent fermées à une adresse publique.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MANAGEMENT_METRICS_EXPOSED=true")
// Sans elle, @SpringBootTest éteint l'export de métriques et l'endpoint
// prometheus n'existe pas — le test verrait 404 quoi que fasse l'application.
@AutoConfigureObservability
@Testcontainers
@AutoConfigureTestRestTemplate
class ActuatorMetriquesExposeesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("/actuator/prometheus répond 200 depuis la boucle locale, sans jeton")
    void prometheusDepuisLaBoucleLocale() {
        ResponseEntity<String> r = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Pas http_server_requests : ce compteur naît avec la première requête servie, et c'est celle-ci.
        assertThat(r.getBody()).contains("labo_disque_libre_pourcent", "labo_sauvegarde_derniere_epoch_secondes");
    }

    @Test
    @DisplayName("/actuator/prometheus est refusé à une adresse publique")
    void prometheusDepuisInternet() {
        HttpHeaders h = new HttpHeaders();
        // forward-headers-strategy=framework : l'adresse du client vient de cet en-tête.
        h.set("X-Forwarded-For", "8.8.8.8");
        ResponseEntity<String> r = rest.exchange("/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("/actuator/health reste public")
    void healthPublic() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Forwarded-For", "8.8.8.8");
        ResponseEntity<String> r = rest.exchange("/actuator/health", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
