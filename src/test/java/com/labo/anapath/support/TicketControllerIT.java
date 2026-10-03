package com.labo.anapath.support;

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
class TicketControllerIT {

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
    @Autowired private TicketRepository ticketRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_ticket_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID adminUserId;

    @BeforeEach
    void setup() {
        var existingAdmin = userRepository.findByEmail(ADMIN_EMAIL);
        if (existingAdmin.isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("TicketTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            adminUserId = userRepository.save(admin).getId();
        } else {
            adminUserId = existingAdmin.get().getId();
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
    @DisplayName("POST /tickets → 201 avec ticket_code généré")
    void create_returns201WithTicketCode() {
        String token = loginAndGetToken();
        Map<String, String> body = Map.of("title", "Problème rapport", "description", "PDF non généré", "priority", "HIGH");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("ticketCode")).asString().startsWith("TI-");
    }

    @Test
    @DisplayName("PATCH /tickets/{id}/status?status=RESOLVED → 200")
    void updateStatus_returns200() {
        String token = loginAndGetToken();

        // Create ticket first
        Map<String, String> createBody = Map.of("title", "Ticket status test", "priority", "LOW");
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets",
                HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        String ticketId = created.getBody().data().get("id").toString();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets/" + ticketId + "/status?status=RESOLVED",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("status")).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("POST /tickets/{id}/comments → 201 commentaire créé")
    void addComment_returns201() {
        String token = loginAndGetToken();

        // Create ticket
        Map<String, String> createBody = Map.of("title", "Ticket comment test", "priority", "MEDIUM");
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets",
                HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        String ticketId = created.getBody().data().get("id").toString();

        Map<String, String> commentBody = Map.of("content", "Problème reproduit sur env test");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets/" + ticketId + "/comments",
                HttpMethod.POST,
                new HttpEntity<>(commentBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("content")).isEqualTo("Problème reproduit sur env test");
    }

    @Test
    @DisplayName("GET /tickets/{id}/comments → 200 liste ordonnée")
    void getComments_returns200() {
        String token = loginAndGetToken();

        // Create ticket
        Map<String, String> createBody = Map.of("title", "Ticket multi-comment test", "priority", "MEDIUM");
        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets",
                HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        String ticketId = created.getBody().data().get("id").toString();

        // Add two comments
        for (String msg : new String[]{"Premier commentaire", "Deuxième commentaire"}) {
            restTemplate.exchange(
                    "http://localhost:" + port + "/api/v1/tickets/" + ticketId + "/comments",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("content", msg), authHeaders(token)),
                    new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});
        }

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets/" + ticketId + "/comments",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("POST /problem-categories → 201 crée une catégorie")
    void createProblemCategory_returns201() {
        String token = loginAndGetToken();
        Map<String, String> body = Map.of("name", "Facturation IT " + UUID.randomUUID().toString().substring(0, 4));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/problem-categories",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("GET /tickets sans token → 401")
    void findAll_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/tickets",
                HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
