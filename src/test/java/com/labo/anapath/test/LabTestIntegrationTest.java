package com.labo.anapath.test;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class LabTestIntegrationTest {

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
    private LabTestRepository labTestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_labtest_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("LabTest Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/lab-tests";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("POST /lab-tests - crée une analyse → 201 avec status ACTIF par défaut")
    void createLabTest_returns201() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        LabTestRequestDto dto = new LabTestRequestDto();
        dto.setName("NFS Test " + UUID.randomUUID().toString().substring(0, 6));
        dto.setPrice(new BigDecimal("5000"));

        ResponseEntity<ApiResponse<LabTestResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().status()).isEqualTo("ACTIF");

        labTestRepository.findById(response.getBody().data().id())
                .ifPresent(labTestRepository::delete);
    }

    @Test
    @DisplayName("POST /lab-tests - doublon de nom → 409 Conflict")
    void createLabTest_duplicateName_returns409() {
        String token = loginAndGetToken();

        LabTest seed = new LabTest();
        seed.setName("Analyse Doublon Test");
        seed.setPrice(new BigDecimal("3000"));
        seed.setStatus("ACTIF");
        seed.setBranchId(SEED_BRANCH_ID);
        LabTest saved = labTestRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            LabTestRequestDto dto = new LabTestRequestDto();
            dto.setName("analyse doublon test");
            dto.setPrice(new BigDecimal("3000"));

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            labTestRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /lab-tests/search?q=NFS → 200 liste filtrée")
    void searchLabTests_returnsMatchingResults() {
        String token = loginAndGetToken();

        LabTest seed = new LabTest();
        seed.setName("NFS Recherche Test");
        seed.setPrice(new BigDecimal("4000"));
        seed.setStatus("ACTIF");
        seed.setBranchId(SEED_BRANCH_ID);
        LabTest saved = labTestRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<List<LabTestResponseDto>>> response = restTemplate.exchange(
                    baseUrl() + "/search?q=NFS",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isNotEmpty();
            assertThat(response.getBody().data().stream()
                    .anyMatch(lt -> lt.name().contains("NFS"))).isTrue();
        } finally {
            labTestRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /lab-tests/search?q=<terme inexistant> → 200 liste vide")
    void searchLabTests_noMatch_returnsEmptyList() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        String uniqueQuery = "ANALYSE_INEXISTANTE_" + UUID.randomUUID().toString().replace("-", "");

        ResponseEntity<ApiResponse<List<LabTestResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "/search?q=" + uniqueQuery,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
    }

    @Test
    @DisplayName("GET /lab-tests - pagination → 200")
    void listLabTests_returns200() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<PageResponse<LabTestResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /lab-tests/{id} - suppression réussie → 200")
    void deleteLabTest_returns200() {
        String token = loginAndGetToken();

        LabTest seed = new LabTest();
        seed.setName("Analyse To Delete " + UUID.randomUUID().toString().substring(0, 6));
        seed.setPrice(new BigDecimal("2000"));
        seed.setStatus("ACTIF");
        seed.setBranchId(SEED_BRANCH_ID);
        LabTest saved = labTestRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(labTestRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /lab-tests - sans token → 401")
    void getLabTests_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
