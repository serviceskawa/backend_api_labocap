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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class SupplierCategoryControllerIT {

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

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_supcat_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("SupCatTest");
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
        return "http://localhost:" + port + "/api/v1/supplier-categories";
    }

    private String createCategory(String token, String name) {
        Map<String, String> body = Map.of("name", name);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().data().get("id").toString();
    }

    @Test
    @DisplayName("CRUD complet catégorie → create → findAll → update → delete")
    void fullCrudCycle_supplierCategory() {
        String token = loginAndGetToken();

        String catId = createCategory(token, "Réactifs");

        ResponseEntity<ApiResponse<List<?>>> listResponse = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> updateBody = Map.of("name", "Réactifs chimiques", "description", "Produits chimiques");
        ResponseEntity<ApiResponse<Map<String, Object>>> putResponse = restTemplate.exchange(
                baseUrl() + "/" + catId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)), new ParameterizedTypeReference<>() {});
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody().data().get("name")).isEqualTo("Réactifs chimiques");

        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                baseUrl() + "/" + catId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("DELETE catégorie liée à un fournisseur → 422")
    void deleteCategory_linkedToSupplier_returns422() {
        String token = loginAndGetToken();
        String catId = createCategory(token, "Matériel médical");

        Map<String, Object> supplierBody = Map.of(
                "name", "Med Supplies SA",
                "categoryId", catId);
        restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/suppliers", HttpMethod.POST,
                new HttpEntity<>(supplierBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                baseUrl() + "/" + catId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("POST /supplier-categories sans name → 400")
    void createCategory_missingName_returns400() {
        String token = loginAndGetToken();
        Map<String, String> body = Map.of("description", "desc seulement");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PUT /supplier-categories/{unknownId} → 404")
    void updateCategory_unknownId_returns404() {
        String token = loginAndGetToken();
        Map<String, String> body = Map.of("name", "Test");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(), HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /supplier-categories sans token → 401")
    void findAll_withoutToken_returns401() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
