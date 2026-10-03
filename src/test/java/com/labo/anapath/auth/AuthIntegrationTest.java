package com.labo.anapath.auth;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.labo.anapath.testsupport.CourrielsDeTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    /**
     * Le limiteur n'accepte que cinq connexions par minute et par adresse : chaque
     * appel se présente depuis une adresse inédite, comme autant de postes.
     */
    private static final java.util.concurrent.atomic.AtomicInteger POSTE = new java.util.concurrent.atomic.AtomicInteger();

    private HttpHeaders depuisUnPosteInedit() {
        int n = POSTE.incrementAndGet();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.9." + (n / 250) + "." + (n % 250 + 1));
        return headers;
    }

    private static final String TEST_EMAIL = "test@labo.bj";
    private static final String TEST_PASSWORD = "password123";
    private static final String INACTIVE_EMAIL = "inactive@labo.bj";

    @BeforeEach
    void seedUser() {
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) {
            User user = new User();
            user.setBranchId(UUID.randomUUID());
            user.setFirstname("Test");
            user.setLastname("User");
            user.setEmail(TEST_EMAIL);
            user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
            user.setActive(true);
            userRepository.save(user);
        }
        if (userRepository.findByEmail(INACTIVE_EMAIL).isEmpty()) {
            User inactive = new User();
            inactive.setBranchId(UUID.randomUUID());
            inactive.setFirstname("Inactive");
            inactive.setLastname("User");
            inactive.setEmail(INACTIVE_EMAIL);
            inactive.setPassword(passwordEncoder.encode(TEST_PASSWORD));
            inactive.setActive(false);
            userRepository.save(inactive);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/auth";
    }

    /** Extrait la valeur d'un cookie depuis l'en-tête {@code Set-Cookie} d'une réponse. */
    private String extractSetCookie(ResponseEntity<?> response, String cookieName) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        return setCookies.stream()
                .filter(h -> h.startsWith(cookieName + "="))
                .map(h -> h.split(";")[0].substring(cookieName.length() + 1))
                .findFirst()
                .orElse(null);
    }

    /** Construit des headers HTTP avec un cookie nommé (pour simuler le navigateur). */
    private HttpHeaders withCookie(String name, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, name + "=" + value);
        return headers;
    }

    /**
     * Connexion complète : mot de passe, puis code à usage unique lu à la sortie
     * du courriel de test. La réponse rendue est celle du challenge, qui porte
     * les cookies — comme ce que reçoit le navigateur.
     */
    private ResponseEntity<ApiResponse<LoginResponse>> doLogin(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        ResponseEntity<ApiResponse<LoginResponse>> premiere = restTemplate.exchange(
                baseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>(req, depuisUnPosteInedit()),
                new ParameterizedTypeReference<>() {});
        if (premiere.getStatusCode() != HttpStatus.OK
                || !Boolean.TRUE.equals(premiere.getBody().data().requires2fa())) {
            return premiere;
        }
        TwoFactorVerifyRequest challenge = new TwoFactorVerifyRequest();
        challenge.setTempToken(premiere.getBody().data().tempToken());
        challenge.setCode(CourrielsDeTest.dernierCode());
        return restTemplate.exchange(
                baseUrl() + "/2fa/challenge",
                HttpMethod.POST,
                new HttpEntity<>(challenge),
                new ParameterizedTypeReference<>() {});
    }

    @Test
    @DisplayName("POST /login - credentials valides → 200 + cookies HttpOnly")
    void loginSuccess_setsCookies() {
        ResponseEntity<ApiResponse<LoginResponse>> response = doLogin(TEST_EMAIL, TEST_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();

        // Les tokens sont dans les cookies Set-Cookie, pas dans le JSON
        String accessCookie  = extractSetCookie(response, "access_token");
        String refreshCookie = extractSetCookie(response, "refresh_token");
        assertThat(accessCookie).isNotBlank();
        assertThat(refreshCookie).isNotBlank();

        // Le JSON ne contient que les métadonnées
        LoginResponse data = response.getBody().data();
        assertThat(data.expiresIn()).isGreaterThan(0);
        assertThat(data.user().email()).isEqualTo(TEST_EMAIL);
        // Tokens absents du JSON
        assertThat(data.accessToken()).isNull();
        assertThat(data.refreshToken()).isNull();
    }

    @Test
    @DisplayName("POST /login - mauvais password → 401")
    void loginInvalid_returns401() {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword("wrong-password");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/login", HttpMethod.POST,
                new HttpEntity<>(request, depuisUnPosteInedit()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /logout puis requête avec même token → 401")
    void logout_thenTokenRejected() {
        // 1. Login → récupère l'access token depuis le cookie
        ResponseEntity<ApiResponse<LoginResponse>> loginResp = doLogin(TEST_EMAIL, TEST_PASSWORD);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = extractSetCookie(loginResp, "access_token");
        assertThat(accessToken).isNotBlank();

        // 2. Logout en envoyant le cookie access_token
        ResponseEntity<String> logoutResp = restTemplate.exchange(
                baseUrl() + "/logout",
                HttpMethod.POST,
                new HttpEntity<>(withCookie("access_token", accessToken)),
                String.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Même cookie après logout → 401 (token blacklisté)
        ResponseEntity<String> rejectedResp = restTemplate.exchange(
                baseUrl() + "/logout",
                HttpMethod.POST,
                new HttpEntity<>(withCookie("access_token", accessToken)),
                String.class);
        assertThat(rejectedResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /refresh - cookie refresh_token valide → nouveaux cookies (AC8)")
    void refreshToken_valid_returnsNewCookies() {
        // 1. Login → extrait les deux cookies
        ResponseEntity<ApiResponse<LoginResponse>> loginResp = doLogin(TEST_EMAIL, TEST_PASSWORD);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String originalRefreshToken = extractSetCookie(loginResp, "refresh_token");
        String originalAccessToken  = extractSetCookie(loginResp, "access_token");
        assertThat(originalRefreshToken).isNotBlank();

        // 2. Envoie le cookie refresh_token → nouveaux cookies
        ResponseEntity<ApiResponse<LoginResponse>> refreshResp = restTemplate.exchange(
                baseUrl() + "/refresh",
                HttpMethod.POST,
                new HttpEntity<>(withCookie("refresh_token", originalRefreshToken)),
                new ParameterizedTypeReference<>() {});

        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccessToken = extractSetCookie(refreshResp, "access_token");
        assertThat(newAccessToken).isNotBlank();
        assertThat(newAccessToken).isNotEqualTo(originalAccessToken);
    }

    @Test
    @DisplayName("POST /refresh - access token en guise de refresh cookie → 401")
    void refreshToken_withAccessCookie_returns401() {
        // Login → extrait l'access token
        ResponseEntity<ApiResponse<LoginResponse>> loginResp = doLogin(TEST_EMAIL, TEST_PASSWORD);
        String accessToken = extractSetCookie(loginResp, "access_token");

        // Envoyer l'access token comme refresh_token cookie → doit être rejeté
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/refresh",
                HttpMethod.POST,
                new HttpEntity<>(withCookie("refresh_token", accessToken)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /login - utilisateur inactif → 401 (AC2)")
    void login_inactiveUser_returns401() {
        LoginRequest request = new LoginRequest();
        request.setEmail(INACTIVE_EMAIL);
        request.setPassword(TEST_PASSWORD);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/login", HttpMethod.POST,
                new HttpEntity<>(request, depuisUnPosteInedit()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Refresh token comme Authorization header → 401 (sécurité)")
    void refreshToken_asAuthorizationHeader_returns401() {
        // Login → extrait le refresh token depuis le cookie
        ResponseEntity<ApiResponse<LoginResponse>> loginResp = doLogin(TEST_EMAIL, TEST_PASSWORD);
        String refreshToken = extractSetCookie(loginResp, "refresh_token");
        assertThat(refreshToken).isNotBlank();

        // Utiliser le refresh token comme Bearer sur un endpoint protégé → 401
        // (JwtAuthenticationFilter rejette les tokens de type "refresh")
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(refreshToken);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/logout",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
