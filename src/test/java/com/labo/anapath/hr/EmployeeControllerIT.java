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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EmployeeControllerIT {

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
    private static final String ADMIN_EMAIL    = "admin_emp_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("EmpTest");
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

    @Test
    @DisplayName("GET /employees → 200 liste paginée")
    void findAll_returns200() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("POST /employees → 201 crée un employé")
    void create_returns201() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "firstName", "Alice",
                "lastName", "Koudossou",
                "salary", "180000.00",
                "hireDate", LocalDate.now().toString());
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("firstName")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("GET /employees/{id} → 200 avec l'employé correspondant")
    void findById_returns200() {
        String token = loginAndGetToken();
        Employee emp = new Employee();
        emp.setBranchId(SEED_BRANCH_ID);
        emp.setFirstName("Bob");
        emp.setLastName("Adandedji");
        emp.setSalary(new BigDecimal("120000"));
        UUID id = employeeRepository.save(emp).getId();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees/" + id,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("lastName")).isEqualTo("Adandedji");
    }

    @Test
    @DisplayName("PUT /employees/{id} - salary null → préserve le salaire existant")
    void update_nullSalary_preservesExisting() {
        String token = loginAndGetToken();
        Employee emp = new Employee();
        emp.setBranchId(SEED_BRANCH_ID);
        emp.setFirstName("Carol");
        emp.setLastName("Houngue");
        emp.setSalary(new BigDecimal("200000"));
        UUID id = employeeRepository.save(emp).getId();

        Map<String, String> body = Map.of("firstName", "Carol-Updated", "lastName", "Houngue");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Employee updated = employeeRepository.findById(id).orElseThrow();
        assertThat(updated.getSalary()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(updated.getFirstName()).isEqualTo("Carol-Updated");
    }

    @Test
    @DisplayName("DELETE /employees/{id} → 200 soft delete")
    void delete_returns200AndSoftDeletes() {
        String token = loginAndGetToken();
        Employee emp = new Employee();
        emp.setBranchId(SEED_BRANCH_ID);
        emp.setFirstName("Dave");
        emp.setLastName("Gbessemehlan");
        emp.setSalary(BigDecimal.ZERO);
        UUID id = employeeRepository.save(emp).getId();

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(employeeRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("GET /employees/{unknownId} → 404")
    void findById_unknownId_returns404() {
        String token = loginAndGetToken();
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /employees - userId inconnu → 404")
    void create_unknownUserId_returns404() {
        String token = loginAndGetToken();
        Map<String, Object> body = Map.of(
                "firstName", "Eve",
                "lastName", "Test",
                "userId", UUID.randomUUID().toString());
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/employees",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
