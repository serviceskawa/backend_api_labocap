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
class CashboxVoucherControllerIT {

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
    @Autowired private CashboxVoucherRepository voucherRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private CashboxRepository cashboxRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_voucher_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("VoucherTest");
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
    @DisplayName("POST /cashbox-tickets → 201 avec statut 'en attente'")
    void create_returns201WithStatusEnAttente() {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("description", "Achat fournitures");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/cashbox-tickets",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("status")).isEqualTo("en attente");
        assertThat(response.getBody().data().get("code")).asString().startsWith("BC");
    }

    @Test
    @DisplayName("PATCH /cashbox-tickets/{id}/status 'approuve' → crée une Expense en base")
    void updateStatus_approuve_createsExpenseInDb() {
        String token = loginAndGetToken();

        // Create voucher
        CashboxVoucher voucher = new CashboxVoucher();
        voucher.setBranchId(SEED_BRANCH_ID);
        voucher.setDescription("Bon test approbation");
        voucher.setAmount(new BigDecimal("750.00"));
        voucher.setStatus("en attente");
        UUID voucherId = voucherRepository.save(voucher).getId();

        long expensesBefore = expenseRepository.count();

        Map<String, String> body = Map.of("status", "approuve");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/cashbox-tickets/" + voucherId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("status")).isEqualTo("approuve");

        long expensesAfter = expenseRepository.count();
        assertThat(expensesAfter).isEqualTo(expensesBefore + 1);
    }

    @Test
    @DisplayName("GET /cashbox-tickets → 200 liste paginée")
    void findAll_returns200() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/cashbox-tickets",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("content");
    }

    @Test
    @DisplayName("POST /cashbox-tickets/{id}/details → met à jour le montant du bon")
    void addDetail_updatesVoucherAmount() {
        String token = loginAndGetToken();

        CashboxVoucher voucher = new CashboxVoucher();
        voucher.setBranchId(SEED_BRANCH_ID);
        voucher.setDescription("Bon avec lignes");
        voucher.setAmount(BigDecimal.ZERO);
        voucher.setStatus("en attente");
        UUID voucherId = voucherRepository.save(voucher).getId();

        Map<String, Object> body = Map.of(
                "itemName", "Gants",
                "quantity", 10,
                "unitPrice", 50);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/cashbox-tickets/" + voucherId + "/details",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CashboxVoucher updated = voucherRepository.findById(voucherId).orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }
}
