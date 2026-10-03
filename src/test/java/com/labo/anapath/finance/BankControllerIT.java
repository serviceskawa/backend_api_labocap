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
class BankControllerIT {

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
    @Autowired private BankRepository bankRepository;
    @Autowired private CashboxRepository cashboxRepository;
    @Autowired private CashboxOperationRepository cashboxOperationRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_bank_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID bankId;
    private UUID cashboxVenteId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("BankTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        Bank bank = new Bank();
        bank.setBranchId(SEED_BRANCH_ID);
        bank.setName("BNI IT Test");
        bank.setAccountNumber("BJ-TEST-001");
        bankId = bankRepository.save(bank).getId();

        // Ensure cashbox vente with enough balance
        Cashbox cashbox = cashboxRepository.findFirstByBranchIdAndType(SEED_BRANCH_ID, "vente")
                .orElseGet(() -> {
                    Cashbox c = new Cashbox();
                    c.setBranchId(SEED_BRANCH_ID);
                    c.setName("Caisse vente IT");
                    c.setType("vente");
                    return cashboxRepository.save(c);
                });
        cashbox.setBalance(new BigDecimal("10000.00"));
        cashboxVenteId = cashboxRepository.save(cashbox).getId();
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
    @DisplayName("POST /banks → 201 crée une banque")
    void create_returns201() {
        String token = loginAndGetToken();

        Map<String, String> body = Map.of(
                "name", "Ecobank Test",
                "accountNumber", "BJ-ECO-001");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/banks",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("name")).isEqualTo("Ecobank Test");
    }

    @Test
    @DisplayName("POST /bank-deposits → 201 débit caisse + CashboxOperation créée")
    void createDeposit_debitsCashboxAndCreatesOperation() {
        String token = loginAndGetToken();

        Cashbox cashbox = cashboxRepository.findById(cashboxVenteId).orElseThrow();
        BigDecimal initialBalance = cashbox.getBalance();

        Map<String, Object> body = Map.of(
                "bankId", bankId.toString(),
                "amount", "1000.00",
                "date", LocalDate.now().toString());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/bank-deposits",
                HttpMethod.POST,
                depotMultipart(body, token),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Cashbox updated = cashboxRepository.findById(cashboxVenteId).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(initialBalance.subtract(new BigDecimal("1000.00")));
    }

    @Test
    @DisplayName("POST /bank-deposits solde insuffisant → 422")
    void createDeposit_insufficientBalance_returns422() {
        String token = loginAndGetToken();

        // Set balance to zero
        Cashbox cashbox = cashboxRepository.findById(cashboxVenteId).orElseThrow();
        cashbox.setBalance(BigDecimal.ZERO);
        cashboxRepository.save(cashbox);

        Map<String, Object> body = Map.of(
                "bankId", bankId.toString(),
                "amount", "500.00",
                "date", LocalDate.now().toString());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/bank-deposits",
                HttpMethod.POST,
                depotMultipart(body, token),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * Le point d'entrée lit une partie « data » (le dépôt) et une partie « file »
     * facultative (le bordereau) : un corps JSON nu n'est plus accepté.
     */
    private HttpEntity<org.springframework.util.MultiValueMap<String, Object>> depotMultipart(Map<String, Object> depot, String token) {
        HttpHeaders enTeteJson = new HttpHeaders();
        enTeteJson.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.util.MultiValueMap<String, Object> parties = new org.springframework.util.LinkedMultiValueMap<>();
        parties.add("data", new HttpEntity<>(depot, enTeteJson));
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parties, headers);
    }
}
