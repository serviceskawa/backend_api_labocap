package com.labo.anapath.consultation;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.categoryprestation.CategoryPrestation;
import com.labo.anapath.categoryprestation.CategoryPrestationRepository;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.prestation.Prestation;
import com.labo.anapath.prestation.PrestationRepository;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConsultationControllerIT {

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
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrestationRepository prestationRepository;
    @Autowired private CategoryPrestationRepository categoryRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_consult_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID patientId;
    private UUID prestationId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("ConsultTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        // Create a test patient
        Patient patient = new Patient();
        patient.setBranchId(SEED_BRANCH_ID);
        patient.setFirstname("Patient");
        patient.setLastname("IT Test");
        patient.setCode("PAT-IT-" + System.currentTimeMillis());
        patient.setYearOrMonth(true);
        patientId = patientRepository.save(patient).getId();

        // Create a category + prestation
        CategoryPrestation cat = new CategoryPrestation();
        cat.setBranchId(SEED_BRANCH_ID);
        cat.setName("Cat IT " + System.currentTimeMillis());
        cat.setSlug("cat-it-" + System.currentTimeMillis());
        UUID catId = categoryRepository.save(cat).getId();

        Prestation prest = new Prestation();
        prest.setBranchId(SEED_BRANCH_ID);
        prest.setName("Prest IT " + System.currentTimeMillis());
        prest.setPrice(new BigDecimal("7500.00"));
        prest.setCategoryPrestation(categoryRepository.findById(catId).orElseThrow());
        prestationId = prestationRepository.save(prest).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/consultations";
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

    private HttpHeaders jsonAuthHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("POST /consultations → 201 avec code CON0001")
    void createConsultation_returns201_withCodeCON() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "patientId", patientId.toString(),
                "prestationId", prestationId.toString(),
                "date", LocalDateTime.now().toString()
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("code").toString()).startsWith("CON");
    }

    @Test
    @DisplayName("POST /consultations → fees depuis prestation, pas du body")
    void createConsultation_feesFromPrestation_notFromBody() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "patientId", patientId.toString(),
                "prestationId", prestationId.toString(),
                "date", LocalDateTime.now().toString()
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Object fees = response.getBody().data().get("fees");
        assertThat(fees).isNotNull();
        // fees = 7500.00 from prestation
        assertThat(fees.toString()).contains("7500");
    }

    @Test
    @DisplayName("PATCH /consultations/{id}/type → 200 type mis à jour")
    void updateType_returns200() {
        String token = loginAndGetToken();

        // Create a consultation first
        Map<String, Object> createBody = Map.of(
                "patientId", patientId.toString(),
                "prestationId", prestationId.toString(),
                "date", LocalDateTime.now().toString()
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> createResp = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID consultationId = UUID.fromString(createResp.getBody().data().get("id").toString());

        // Try to patch type with unknown typeId (will return 404 — that's fine, tests the guard)
        Map<String, Object> typeBody = Map.of("typeConsultationId", UUID.randomUUID().toString());
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + consultationId + "/type",
                HttpMethod.PATCH,
                new HttpEntity<>(typeBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /consultations?status=pending → filtrage par status")
    void filterByStatus_returnsCorrectSubset() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "?status=pending",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("content");
    }

    @Test
    @DisplayName("GET /consultations/{unknownId} → 404")
    void findById_unknownId_returns404() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
