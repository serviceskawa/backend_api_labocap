package com.labo.anapath.auth;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.exception.InvalidCodeException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot 2 : un code à six chiffres se trouve par force brute si rien ne freine
 * les essais. Deux freins, éprouvés ici : par adresse (429 au 6ᵉ essai dans la
 * minute) et par compte (code invalidé au 5ᵉ échec, compte verrouillé au 10ᵉ).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class LimitationDesEssaisIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String MOT_DE_PASSE = "motdepasse-valide";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;
    @LocalServerPort private int port;

    private String url(String chemin) {
        return "http://localhost:" + port + "/api/v1/auth" + chemin;
    }

    private User creerUtilisateur(String email) {
        User user = new User();
        user.setBranchId(UUID.randomUUID());
        user.setFirstname("Essais");
        user.setLastname("Limités");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(MOT_DE_PASSE));
        user.setActive(true);
        return userRepository.save(user);
    }

    private ResponseEntity<ApiResponse<LoginResponse>> connexion(String email) {
        LoginRequest requete = new LoginRequest();
        requete.setEmail(email);
        requete.setPassword(MOT_DE_PASSE);
        return restTemplate.exchange(url("/login"), HttpMethod.POST, new HttpEntity<>(requete),
                new ParameterizedTypeReference<>() {});
    }

    private TwoFactorVerifyRequest codeFaux(String tempToken) {
        TwoFactorVerifyRequest requete = new TwoFactorVerifyRequest();
        requete.setTempToken(tempToken);
        requete.setCode("000000");
        return requete;
    }

    @Test
    @DisplayName("6 codes faux en moins d'une minute depuis la même adresse → le 6ᵉ est refusé en 429")
    void sixiemeEssaiDepuisLaMemeAdresseRefuse() {
        creerUtilisateur("adresse@labo.bj");
        String tempToken = connexion("adresse@labo.bj").getBody().data().tempToken();

        for (int i = 1; i <= 5; i++) {
            ResponseEntity<String> reponse = restTemplate.postForEntity(
                    url("/2fa/challenge"), codeFaux(tempToken), String.class);
            assertThat(reponse.getStatusCode()).as("essai %d", i).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        ResponseEntity<String> sixieme = restTemplate.postForEntity(
                url("/2fa/challenge"), codeFaux(tempToken), String.class);
        assertThat(sixieme.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("10 codes faux → compte verrouillé 15 min, la connexion échoue même avec le bon mot de passe")
    void dixEchecsVerrouillentLeCompte() {
        User user = creerUtilisateur("verrou@labo.bj");
        String tempToken = connexion("verrou@labo.bj").getBody().data().tempToken();

        // Par le service, pour éprouver le frein par compte sans buter sur
        // celui par adresse.
        for (int i = 1; i <= 10; i++) {
            assertThatThrownBy(() -> authService.challenge(codeFaux(tempToken)))
                    .as("essai %d", i).isInstanceOf(InvalidCodeException.class);
        }

        User verrouille = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verrouille.getLockedUntil())
                .isBetween(LocalDateTime.now().plusMinutes(14), LocalDateTime.now().plusMinutes(16));

        ResponseEntity<ApiResponse<LoginResponse>> connexion = connexion("verrou@labo.bj");
        assertThat(connexion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(connexion.getBody().message()).isEqualTo("Identifiants invalides.");
    }

    @Test
    @DisplayName("5 codes faux → le code en cours est invalidé, le bon code ne passe plus")
    void cinqEchecsInvalidentLeCode() {
        creerUtilisateur("code@labo.bj");
        String tempToken = connexion("code@labo.bj").getBody().data().tempToken();
        String bonCode = com.labo.anapath.testsupport.CourrielsDeTest.dernierCode();

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> authService.challenge(codeFaux(tempToken)))
                    .isInstanceOf(InvalidCodeException.class);
        }
        TwoFactorVerifyRequest bon = codeFaux(tempToken);
        bon.setCode(bonCode);
        assertThatThrownBy(() -> authService.challenge(bon))
                .isInstanceOf(InvalidCodeException.class)
                .hasMessageContaining("expiré");
    }
}
