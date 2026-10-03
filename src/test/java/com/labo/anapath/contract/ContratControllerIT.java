package com.labo.anapath.contract;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.test.CategoryTest;
import com.labo.anapath.test.CategoryTestRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContratControllerIT {

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
    @Autowired private CategoryTestRepository categoryTestRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_contract_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID categoryTestId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("ContractTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        CategoryTest cat = new CategoryTest();
        cat.setBranchId(SEED_BRANCH_ID);
        cat.setName("Cat Contract IT " + System.currentTimeMillis());
        categoryTestId = categoryTestRepository.save(cat).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/contracts";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    private HttpHeaders jsonAuthHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    @Test
    @DisplayName("POST /contracts → 201 avec statut INACTIF")
    void createContract_returns201() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "name", "Contrat Test IT",
                "type", "ASSURANCE",
                "startDate", LocalDate.now().toString(),
                "nbrTests", 50,
                "invoiceUnique", false
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("status")).isEqualTo("INACTIF");
        assertThat(response.getBody().data().get("name")).isEqualTo("Contrat Test IT");
    }

    @Test
    @DisplayName("POST /contracts/{id}/details → 201 puis doublon → 422")
    void addCategoryDetail_thenDuplicate_returns422() {
        String token = loginAndGetToken();

        // Create contract
        Map<String, Object> createBody = Map.of(
                "name", "Contrat Detail IT",
                "startDate", LocalDate.now().toString(),
                "nbrTests", 10
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID contractId = UUID.fromString(created.getBody().data().get("id").toString());

        // Add category detail — first time → 201
        Map<String, Object> detailBody = Map.of(
                "categoryTestId", categoryTestId.toString(),
                "discount", "10.00"
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> first = restTemplate.exchange(
                baseUrl() + "/" + contractId + "/details", HttpMethod.POST,
                new HttpEntity<>(detailBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Add same category detail again → 422
        ResponseEntity<ApiResponse<Map<String, Object>>> second = restTemplate.exchange(
                baseUrl() + "/" + contractId + "/details", HttpMethod.POST,
                new HttpEntity<>(detailBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("PATCH /contracts/{id}/status → 200 activation avec statut ACTIF")
    void activateContract_returns200WithActifStatus() {
        String token = loginAndGetToken();

        // Create contract with invoiceUnique=false (no auto invoice)
        Map<String, Object> createBody = Map.of(
                "name", "Contrat Activate IT",
                "startDate", LocalDate.now().toString(),
                "nbrTests", 10,
                "invoiceUnique", false
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID contractId = UUID.fromString(created.getBody().data().get("id").toString());

        // Activate
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + contractId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("status")).isEqualTo("ACTIF");
    }

    @Test
    @DisplayName("POST /contracts/{id}/close → 200 isClose=true")
    void closeContract_returns200() {
        String token = loginAndGetToken();

        // Create contract
        Map<String, Object> createBody = Map.of(
                "name", "Contrat Close IT",
                "startDate", LocalDate.now().toString(),
                "nbrTests", 10,
                "invoiceUnique", false
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID contractId = UUID.fromString(created.getBody().data().get("id").toString());

        // Close
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + contractId + "/close", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("isClose")).isEqualTo(true);
    }
}
