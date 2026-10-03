package com.labo.anapath.finance;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.setting.SettingInvoice;
import com.labo.anapath.setting.SettingInvoiceRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class MecefControllerIT {

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
    @Autowired private SettingInvoiceRepository settingInvoiceRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_mecef_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID invoiceId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("MecefTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        // Facture de test
        Invoice inv = new Invoice();
        inv.setBranchId(SEED_BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        inv.setPaid(false);
        inv.setStatus(InvoiceStatus.PENDING);
        invoiceId = invoiceRepository.save(inv).getId();

        // Seed code_mecef on another invoice for check-code test
        Invoice invCoded = new Invoice();
        invCoded.setBranchId(SEED_BRANCH_ID);
        invCoded.setTotal(BigDecimal.ZERO);
        invCoded.setCodeMecef("FA260001");
        invoiceRepository.save(invCoded);
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/invoices";
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
    @DisplayName("POST /invoices/{id}/confirm-mecef → 422 MECEF_DISABLED (pas de SettingInvoice)")
    void confirmMecef_noSetting_returns422() {
        String token = loginAndGetToken();
        settingInvoiceRepository.deleteAll();

        Map<String, String> body = Map.of("uid", "some-uid");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + invoiceId + "/confirm-mecef", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("POST /invoices/{id}/confirm-mecef → 422 MECEF_DISABLED (status=false)")
    void confirmMecef_statusDisabled_returns422() {
        String token = loginAndGetToken();

        SettingInvoice setting = new SettingInvoice();
        setting.setBranchId(SEED_BRANCH_ID);
        setting.setToken("test-token");
        setting.setStatus(false);
        settingInvoiceRepository.save(setting);

        Map<String, String> body = Map.of("uid", "some-uid");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + invoiceId + "/confirm-mecef", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("GET /invoices/check-code?code=FA260001 → {exists: true}")
    void checkCode_existingMecefCode_returnsTrue() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/check-code?code=FA260001", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("exists")).isEqualTo(true);
    }
}
