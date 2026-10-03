package com.labo.anapath.finance;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RefundRequestControllerIT {

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
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private RefundRequestRepository refundRequestRepository;
    @Autowired private RefundReasonRepository refundReasonRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_refund_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID invoiceId;
    private UUID refundReasonId;

    @BeforeEach
    void setup() {
        // Clean refund requests to avoid unique constraint violations between test runs
        refundRequestRepository.deleteAll();

        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("RefundTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        // Seed a refund reason
        RefundReason reason = new RefundReason();
        reason.setBranchId(SEED_BRANCH_ID);
        reason.setLabel("Erreur de facturation");
        refundReasonId = refundReasonRepository.save(reason).getId();

        // Paid invoice for refund
        Invoice inv = new Invoice();
        inv.setBranchId(SEED_BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        inv.setPaid(true);
        inv.setStatus(InvoiceStatus.PAID);
        inv.setStatusInvoice(0);
        invoiceId = invoiceRepository.save(inv).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/refund-requests";
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
    @DisplayName("POST /refund-requests → 201 avec log initial 'En attente'")
    void createRefund_returns201WithInitialLog() {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of(
                "invoiceId", invoiceId.toString(),
                "refundReasonId", refundReasonId.toString(),
                "montant", "3000.00",
                "note", "Test IT");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> data = response.getBody().data();
        assertThat(data.get("status")).isEqualTo("En attente");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) data.get("logs");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("operation")).isEqualTo("En attente");
    }

    @Test
    @DisplayName("POST /refund-requests → 422 REFUND_ALREADY_EXISTS si doublon invoiceId")
    void createRefund_duplicate_returns422() {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of(
                "invoiceId", invoiceId.toString(),
                "refundReasonId", refundReasonId.toString(),
                "montant", "1000.00");

        // Première création
        restTemplate.exchange(baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

        // Deuxième création → doublon
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("PATCH /refund-requests/{id}/status avec 'Aprouvé' → 200 + avoir créé")
    void updateStatus_Aprouve_returns200WithAvoir() {
        String token = loginAndGetToken();

        // Créer une demande
        Map<String, Object> createBody = Map.of(
                "invoiceId", invoiceId.toString(),
                "refundReasonId", refundReasonId.toString(),
                "montant", "2000.00");

        ResponseEntity<ApiResponse<Map<String, Object>>> created = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(createBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        UUID refundId = UUID.fromString((String) created.getBody().data().get("id"));

        // Approuver
        Map<String, String> statusBody = Map.of("status", "Aprouvé");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + refundId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(statusBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = response.getBody().data();
        assertThat(data.get("status")).isEqualTo("Aprouvé");
        assertThat(data.get("invoiceId")).isNotNull();
    }

    @Test
    @DisplayName("GET /refund-requests → 200 liste paginée")
    void findAll_returns200PagedList() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("content");
    }
}
