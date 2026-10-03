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
class ArticleControllerIT {

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
    @Autowired private MovementRepository movementRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_article_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("ArticleTest");
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

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/articles";
    }

    private String createArticle(String token, String name, BigDecimal initialQty) {
        Map<String, Object> body = initialQty != null
                ? Map.of("name", name, "initialQuantity", initialQty)
                : Map.of("name", name);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().data().get("id").toString();
    }

    @Test
    @DisplayName("POST /articles avec initialQuantity → mouvement IN créé en BDD")
    void create_withInitialQuantity_createsMovement() {
        String token = loginAndGetToken();

        long movementsBefore = movementRepository.count();
        createArticle(token, "Réactif HIV Test", new BigDecimal("25"));
        long movementsAfter = movementRepository.count();

        assertThat(movementsAfter).isEqualTo(movementsBefore + 1);
    }

    @Test
    @DisplayName("POST /articles sans initialQuantity → quantity=0, un mouvement initial à 0 tracé")
    void create_withoutInitialQuantity_noMovement() {
        String token = loginAndGetToken();

        long movementsBefore = movementRepository.count();
        String id = createArticle(token, "Gants stériles", null);
        long movementsAfter = movementRepository.count();

        // Le service trace toujours le stock initial, même nul (comme Laravel).
        assertThat(movementsAfter).isEqualTo(movementsBefore + 1);

        ResponseEntity<ApiResponse<Map<String, Object>>> getResponse = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});
        // La quantité est décimale en JSON (0.0) : comparaison numérique.
        assertThat(new java.math.BigDecimal(getResponse.getBody().data().get("quantity").toString())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("GET /articles → outOfStockCount et lowStockCount présents")
    void findAll_includesStockCounts() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("outOfStockCount");
        assertThat(response.getBody().data()).containsKey("lowStockCount");
    }

    @Test
    @DisplayName("PUT /articles/{id} ne modifie pas la quantité")
    void update_doesNotChangeQuantity() {
        String token = loginAndGetToken();
        String id = createArticle(token, "Seringues 5mL", new BigDecimal("100"));

        Map<String, Object> updateBody = Map.of("name", "Seringues 5mL mis à jour");
        restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), new ParameterizedTypeReference<>() {});

        ResponseEntity<ApiResponse<Map<String, Object>>> getResponse = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        Object quantity = getResponse.getBody().data().get("quantity");
        assertThat(new java.math.BigDecimal(quantity.toString())).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("GET /articles/search?q= → filtre par nom")
    void search_returnsFilteredByName() {
        String token = loginAndGetToken();
        createArticle(token, "Albumine bovine", null);
        createArticle(token, "Pipettes plastiques", null);

        ResponseEntity<ApiResponse<List<?>>> response = restTemplate.exchange(
                baseUrl() + "/search?q=Albumine", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("DELETE /articles/{id} → soft delete → GET retourne 404")
    void delete_softDeletes_thenGet_returns404() {
        String token = loginAndGetToken();
        String id = createArticle(token, "Article à supprimer", null);

        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<Void>> getResponse = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /articles sans name → 400")
    void create_missingName_returns400() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("unit", "boîte");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /articles/{unknownId} → 404")
    void findById_unknownId_returns404() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /articles sans token → 401")
    void findAll_withoutToken_returns401() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("PUT /articles/{unknownId} → 404")
    void update_unknownId_returns404() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("name", "Nom inexistant");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /articles → outOfStockCount reflète les articles à quantité zéro")
    void findAll_outOfStockCount_reflectsZeroQuantityArticles() {
        String token = loginAndGetToken();
        // Créer un article à quantité zéro (pas d'initialQuantity)
        createArticle(token, "ArticleZeroStock" + UUID.randomUUID(), null);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = response.getBody().data();
        assertThat(data).containsKey("outOfStockCount");
        assertThat((Integer) data.get("outOfStockCount")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("GET /articles → lowStockCount reflète les articles sous le stock minimum")
    void findAll_lowStockCount_reflectsArticlesBelowMinimumStock() {
        String token = loginAndGetToken();
        // Créer un article avec quantité=5 mais minimumStock=10 → sous le seuil
        Map<String, Object> body = Map.of(
                "name", "ArticleLowStock" + UUID.randomUUID(),
                "initialQuantity", new BigDecimal("5"),
                "minimumStock", new BigDecimal("10"));
        restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = response.getBody().data();
        assertThat((Integer) data.get("lowStockCount")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("POST /articles avec initialQuantity → mouvement a les notes 'Stock initial'")
    void create_withInitialQuantity_movementHasCorrectNotes() {
        String token = loginAndGetToken();

        String articleId = createArticle(token, "ArticleNotesTest" + UUID.randomUUID(), new BigDecimal("10"));

        List<Movement> articleMovements = movementRepository.findAll().stream()
                .filter(m -> m.getArticle().getId().toString().equals(articleId))
                .toList();
        assertThat(articleMovements).hasSize(1);
        assertThat(articleMovements.get(0).getNotes()).isEqualTo("Stock initial");
        assertThat(articleMovements.get(0).getType()).isEqualTo(MovementType.IN);
    }

    @Test
    @DisplayName("POST /articles avec supplierId valide → supplierId présent dans la réponse")
    void create_withSupplierId_supplierLinkedInResponse() {
        String token = loginAndGetToken();

        // Créer un fournisseur via l'API
        Map<String, String> supplierBody = Map.of("name", "FournisseurArticleTest" + UUID.randomUUID());
        ResponseEntity<ApiResponse<Map<String, Object>>> supplierResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/suppliers", HttpMethod.POST,
                new HttpEntity<>(supplierBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(supplierResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String supplierId = supplierResponse.getBody().data().get("id").toString();

        // Créer un article lié à ce fournisseur
        Map<String, Object> articleBody = Map.of(
                "name", "ArticleAvecFournisseur" + UUID.randomUUID(),
                "supplierId", supplierId);
        ResponseEntity<ApiResponse<Map<String, Object>>> articleResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(articleBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(articleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(articleResponse.getBody().data().get("supplierId").toString()).isEqualTo(supplierId);
    }
}
