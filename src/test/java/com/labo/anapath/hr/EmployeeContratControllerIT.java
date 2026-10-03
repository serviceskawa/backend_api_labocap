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
class EmployeeContratControllerIT {

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
    private static final String ADMIN_EMAIL    = "admin_contrat_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID employeeId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("ContratTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        Employee emp = new Employee();
        emp.setBranchId(SEED_BRANCH_ID);
        emp.setFirstName("Frederic");
        emp.setLastName("Akindele");
        emp.setSalary(new BigDecimal("300000"));
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
        return "http://localhost:" + port + "/api/v1/employees/" + employeeId + "/contrats";
    }

    @Test
    @DisplayName("POST .../contrats → 201 crée un contrat")
    void createContrat_returns201() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "startDate", LocalDate.now().toString(),
                "type", "CDI",
                "salary", "350000.00");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("type")).isEqualTo("CDI");
    }

    @Test
    @DisplayName("GET .../contrats → 200 liste paginée")
    void findAllContrats_returns200() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("PUT .../contrats/{id} → 200 mise à jour")
    void updateContrat_returns200() {
        String token = loginAndGetToken();

        // Créer un contrat via API
        Map<String, Object> createBody = Map.of(
                "startDate", LocalDate.now().toString(),
                "salary", "200000.00");
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        String contratId = created.getBody().data().get("id").toString();

        // Mettre à jour
        Map<String, Object> updateBody = Map.of(
                "startDate", LocalDate.now().toString(),
                "salary", "250000.00",
                "type", "CDD");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + contratId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("type")).isEqualTo("CDD");
    }

    @Test
    @DisplayName("DELETE .../contrats/{id} → 200 suppression hard delete")
    void deleteContrat_returns200() {
        String token = loginAndGetToken();

        Map<String, Object> createBody = Map.of(
                "startDate", LocalDate.now().toString(),
                "salary", "180000.00");
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        String contratId = created.getBody().data().get("id").toString();

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + contratId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("POST .../contrats - startDate absente → 400")
    void createContrat_missingStartDate_returns400() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("salary", "200000.00");
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST .../contrats - salary absent → 400")
    void createContrat_missingSalary_returns400() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of("startDate", LocalDate.now().toString());
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST .../contrats - employeeId inconnu → 404")
    void createContrat_unknownEmployee_returns404() {
        String token = loginAndGetToken();
        String unknownUrl = "http://localhost:" + port + "/api/v1/employees/" + UUID.randomUUID() + "/contrats";
        Map<String, Object> body = Map.of(
                "startDate", LocalDate.now().toString(),
                "salary", "300000.00");
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                unknownUrl, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET .../contrats sans token → 401")
    void findAllContrats_withoutToken_returns401() {
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
