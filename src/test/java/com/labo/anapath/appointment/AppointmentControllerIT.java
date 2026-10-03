package com.labo.anapath.appointment;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AppointmentControllerIT {

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

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_appt_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID patientId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("ApptTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        Patient patient = new Patient();
        patient.setBranchId(SEED_BRANCH_ID);
        patient.setFirstname("Patient");
        patient.setLastname("RDV Test");
        patient.setCode("PAT-RDV-" + System.currentTimeMillis());
        patient.setYearOrMonth(true);
        patientId = patientRepository.save(patient).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/appointments";
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
    @DisplayName("POST /appointments → 201 avec statut pending")
    void createAppointment_returns201() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "patientId", patientId.toString(),
                "time", LocalDateTime.now().toString(),
                "priority", "normal"
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("status")).isEqualTo("pending");
        assertThat(response.getBody().data().get("priority")).isEqualTo("normal");
    }

    @Test
    @DisplayName("GET /appointments/calendar → liste non paginée d'événements")
    void getCalendar_returnsEventList_notPaginated() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                baseUrl() + "/calendar", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("PUT /appointments/{id} → 200 mise à jour")
    void updateAppointment_returns200() {
        String token = loginAndGetToken();

        // Create
        Map<String, Object> createBody = Map.of(
                "patientId", patientId.toString(),
                "time", LocalDateTime.now().toString(),
                "priority", "normal"
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID apptId = UUID.fromString(created.getBody().data().get("id").toString());

        // Update
        Map<String, Object> updateBody = Map.of(
                "patientId", patientId.toString(),
                "time", LocalDateTime.now().plusHours(1).toString(),
                "priority", "urgent",
                "message", "Mise à jour test"
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + apptId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("priority")).isEqualTo("urgent");
    }

    @Test
    @DisplayName("DELETE /appointments/{id} → soft delete (204 / 404 après)")
    void deleteAppointment_softDeletesRow() {
        String token = loginAndGetToken();

        // Create
        Map<String, Object> createBody = Map.of(
                "patientId", patientId.toString(),
                "time", LocalDateTime.now().toString()
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID apptId = UUID.fromString(created.getBody().data().get("id").toString());

        // Delete
        ResponseEntity<ApiResponse<Void>> del = restTemplate.exchange(
                baseUrl() + "/" + apptId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify soft-deleted (404)
        ResponseEntity<ApiResponse<Map<String, Object>>> get = restTemplate.exchange(
                baseUrl() + "/" + apptId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /appointments/{id}/consultation → 201 consultation créée")
    void createConsultationFromAppointment_returns201() {
        String token = loginAndGetToken();

        // Create appointment
        Map<String, Object> createBody = Map.of(
                "patientId", patientId.toString(),
                "time", LocalDateTime.now().toString()
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID apptId = UUID.fromString(created.getBody().data().get("id").toString());

        // Create consultation from appointment
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + apptId + "/consultation", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("code").toString()).startsWith("CON");
    }

    @Test
    @DisplayName("POST /appointments/{id}/consultation → idempotent (2e appel retourne 200 + même consultation)")
    void createConsultationFromAppointment_idempotent_returns200() {
        String token = loginAndGetToken();

        // Create appointment
        Map<String, Object> createBody = Map.of(
                "patientId", patientId.toString(),
                "time", LocalDateTime.now().toString()
        );
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, jsonAuthHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID apptId = UUID.fromString(created.getBody().data().get("id").toString());

        // First call → 201
        ResponseEntity<ApiResponse<Map<String, Object>>> first = restTemplate.exchange(
                baseUrl() + "/" + apptId + "/consultation", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firstCode = first.getBody().data().get("code").toString();

        // Second call (idempotent) → 200 + same code
        ResponseEntity<ApiResponse<Map<String, Object>>> second = restTemplate.exchange(
                baseUrl() + "/" + apptId + "/consultation", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().data().get("code").toString()).isEqualTo(firstCode);
    }
}
