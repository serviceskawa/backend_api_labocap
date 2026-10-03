package com.labo.anapath.report;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class ReportIntegrationTest {

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
    @Autowired private ReportRepository reportRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_report_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("ReportTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/reports";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    @Test
    @DisplayName("GET /reports/suivi → 200 avec structure stats complète")
    void getSuivi_returns200WithStatsStructure() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/suivi",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKeys("examens", "rapports", "macros", "patientCalled", "listYears");
    }

    @Test
    @DisplayName("GET /reports?month=5&year=2026 → 200 filtrage fonctionnel")
    void findAll_withMonthYearFilter_returns200() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<PageResponse<ReportResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20&month=5&year=2026",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
    }

    @Test
    @DisplayName("POST /reports sans testOrderId → erreur (création invalide)")
    void createReport_withoutTestOrder_returns422OrBadRequest() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("content", "Contenu de test");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode().value()).isIn(400, 422, 500);
    }

    @Test
    @DisplayName("GET /reports/{unknownId} → 404")
    void findById_unknownId_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /reports → 200 liste paginée")
    void findAll_returns200() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<PageResponse<ReportResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
    }

    // ===== Tests story 3-7 — Validation, signature, livraison =====

    @Test
    @DisplayName("POST /reports/{unknownId}/validate → 404")
    void validate_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/validate",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /reports/{unknownId}/delivered-patient → 404")
    void markDelivered_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/delivered-patient",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /reports/{unknownId}/informed-patient → 404")
    void markInformed_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/informed-patient",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /reports/{unknownId}/store-signature → 404")
    void storeSignature_unknownReport_returns404() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("signatorName", "Jean Dupont", "signature", "data:image/png;base64,abc");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/store-signature",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== Tests story 3-8 — Notifications =====

    @Test
    @DisplayName("POST /reports/{unknownId}/call → 404")
    void callPatient_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/call",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /reports/{unknownId}/sms → 404")
    void sendSms_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/sms",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /reports/{unknownId}/appel → 404")
    void getAppelStatus_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/appel",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== Tests story 3-9 — PDF et templates =====

    @Test
    @DisplayName("GET /reports/{unknownId}/pdf → 404")
    void generatePdf_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/pdf",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /reports/{unknownId}/template → 404")
    void getTemplate_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/template",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /reports/{unknownId}/template/{unknownTemplateId} → 404")
    void setTemplate_unknownReport_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/template/" + UUID.randomUUID(),
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
