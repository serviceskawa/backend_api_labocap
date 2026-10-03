package com.labo.anapath.hr;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class EmployeeTimeoffControllerIT {

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
    @Autowired private EmployeeRepository employeeRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_timeoff_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID employeeId;
    private UUID linkedUserId;

    @BeforeEach
    void setup() {
        // Créer l'admin pour les tests
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("TimeoffTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        // Créer un User lié à l'employé pour tester la cascade
        String linkedEmail = "emp_linked_" + UUID.randomUUID() + "@labo.bj";
        Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
        User linkedUser = new User();
        linkedUser.setBranchId(SEED_BRANCH_ID);
        linkedUser.setFirstname("EmpLinked");
        linkedUser.setLastname("User");
        linkedUser.setEmail(linkedEmail);
        linkedUser.setPassword(passwordEncoder.encode("pass"));
        linkedUser.setActive(true);
        linkedUser.setRoles(List.of(adminRole));
        linkedUserId = userRepository.save(linkedUser).getId();

        // Créer l'employé lié à ce User
        Employee emp = new Employee();
        emp.setBranchId(SEED_BRANCH_ID);
        emp.setFirstName("Henri");
        emp.setLastName("Lovenou");
        emp.setSalary(new BigDecimal("250000"));
        emp.setUser(linkedUser);
        employeeId = employeeRepository.save(emp).getId();
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
        return "http://localhost:" + port + "/api/v1/employees/" + employeeId + "/timeoffs";
    }

    private String createTimeoff(String token, String startDate, String endDate) {
        Map<String, String> body = Map.of("startDate", startDate, "endDate", endDate);
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().get("id").toString();
    }

    @Test
    @DisplayName("POST .../timeoffs → 201 avec statut PENDING")
    void createTimeoff_returns201_withPendingStatus() {
        String token = loginAndGetToken();
        Map<String, String> body = Map.of(
                "startDate", "2026-07-01",
                "endDate", "2026-07-14",
                "reason", "Congés annuels");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("GET .../timeoffs → 200 liste paginée")
    void findAllTimeoffs_returns200() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET .../timeoffs/{id} → 200")
    void findById_returns200() {
        String token = loginAndGetToken();
        String tid = createTimeoff(token, "2026-08-01", "2026-08-07");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + tid, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET .../timeoffs/{unknownId} → 404")
    void findById_unknownId_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUT .../timeoffs/{id}/status → APPROVED désactive le User lié")
    void updateStatus_toApproved_deactivatesLinkedUser() {
        String token = loginAndGetToken();
        String tid = createTimeoff(token, "2026-09-01", "2026-09-15");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + tid + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "APPROVED"), authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("status")).isEqualTo("APPROVED");

        // Vérifier que le User lié est désactivé en BDD
        User linkedUser = userRepository.findById(linkedUserId).orElseThrow();
        assertThat(linkedUser.isActive()).isFalse();
    }

    @Test
    @DisplayName("PUT .../timeoffs/{id}/status APPROVED → REJECTED : réactive le User")
    void updateStatus_toRejected_fromApproved_reactivatesUser() {
        String token = loginAndGetToken();
        String tid = createTimeoff(token, "2026-10-01", "2026-10-10");

        // Approuver d'abord
        restTemplate.exchange(
                baseUrl() + "/" + tid + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "APPROVED"), authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        // Puis rejeter
        restTemplate.exchange(
                baseUrl() + "/" + tid + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "REJECTED"), authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        // Le User doit être réactivé
        User linkedUser = userRepository.findById(linkedUserId).orElseThrow();
        assertThat(linkedUser.isActive()).isTrue();
    }

    @Test
    @DisplayName("DELETE .../timeoffs/{id} → 200 si PENDING")
    void deleteTimeoff_pending_returns200() {
        String token = loginAndGetToken();
        String tid = createTimeoff(token, "2026-11-01", "2026-11-07");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + tid, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("DELETE .../timeoffs/{id} → 422 si APPROVED")
    void deleteTimeoff_approved_returns422() {
        String token = loginAndGetToken();
        String tid = createTimeoff(token, "2026-12-01", "2026-12-07");

        // Approuver
        restTemplate.exchange(
                baseUrl() + "/" + tid + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "APPROVED"), authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        // Tenter de supprimer
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + tid, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("POST .../timeoffs sans token → 401")
    void createTimeoff_withoutToken_returns401() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(Map.of("startDate", "2026-07-01", "endDate", "2026-07-14"),
                        new HttpHeaders()),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
