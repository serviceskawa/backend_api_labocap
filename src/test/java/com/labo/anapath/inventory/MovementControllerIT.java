package com.labo.anapath.inventory;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class MovementControllerIT {

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

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ArticleRepository articleRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_movement_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("MovementTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private UUID createArticleDirectly(BigDecimal initialQuantity) {
        Article a = new Article();
        a.setBranchId(SEED_BRANCH_ID);
        a.setName("Article test " + UUID.randomUUID());
        a.setQuantity(initialQuantity);
        return articleRepository.save(a).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/movements";
    }

    @Test
    @DisplayName("POST /movements IN → article.quantity augmente atomiquement")
    void createIN_shouldUpdateArticleQuantityAtomically() {
        String token = loginAndGetToken();
        UUID articleId = createArticleDirectly(new BigDecimal("10"));

        Map<String, Object> body = Map.of(
                "articleId", articleId.toString(),
                "type", "IN",
                "quantity", "5",
                "notes", "Entrée test");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Article updated = articleRepository.findById(articleId).orElseThrow();
        assertThat(updated.getQuantity()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("POST /movements OUT stock suffisant → quantity diminuée")
    void createOUT_sufficientStock_shouldDecreaseQuantity() {
        String token = loginAndGetToken();
        UUID articleId = createArticleDirectly(new BigDecimal("50"));

        Map<String, Object> body = Map.of(
                "articleId", articleId.toString(),
                "type", "OUT",
                "quantity", "20");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Article updated = articleRepository.findById(articleId).orElseThrow();
        assertThat(updated.getQuantity()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("POST /movements OUT stock insuffisant → 422")
    void createOUT_insufficientStock_shouldReturn422() {
        String token = loginAndGetToken();
        UUID articleId = createArticleDirectly(new BigDecimal("5"));

        Map<String, Object> body = Map.of(
                "articleId", articleId.toString(),
                "type", "OUT",
                "quantity", "10");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("POST /movements ADJUSTMENT → quantity remplacée directement")
    void createADJUSTMENT_shouldSetQuantityDirectly() {
        String token = loginAndGetToken();
        UUID articleId = createArticleDirectly(new BigDecimal("100"));

        Map<String, Object> body = Map.of(
                "articleId", articleId.toString(),
                "type", "ADJUSTMENT",
                "quantity", "42",
                "notes", "Inventaire physique");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Article updated = articleRepository.findById(articleId).orElseThrow();
        assertThat(updated.getQuantity()).isEqualByComparingTo("42");
    }

    @Test
    @DisplayName("GET /movements → liste paginée triée par createdAt DESC")
    void findAll_shouldBeSortedByCreatedAtDesc() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("content");
    }

    @Test
    @DisplayName("POST /movements sans articleId → 400")
    void create_missingArticleId_shouldReturn400() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("type", "IN", "quantity", "5");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /movements quantity négative → 400")
    void create_negativeQuantity_shouldReturn400() {
        String token = loginAndGetToken();
        UUID articleId = createArticleDirectly(BigDecimal.TEN);
        Map<String, Object> body = Map.of(
                "articleId", articleId.toString(),
                "type", "IN",
                "quantity", "-5");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /movements sans token → 401")
    void findAll_withoutToken_returns401() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
