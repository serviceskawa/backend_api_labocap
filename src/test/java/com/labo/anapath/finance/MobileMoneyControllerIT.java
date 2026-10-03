package com.labo.anapath.finance;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.setting.Setting;
import com.labo.anapath.setting.SettingRepository;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class MobileMoneyControllerIT {

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
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SettingRepository settingRepository;
    @Autowired private RestTemplate internalRestTemplate;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_mm_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID invoiceId;
    private UUID paymentId;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockServer = MockRestServiceServer.createServer(internalRestTemplate);

        // Clean state
        paymentRepository.deleteAll(paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentName() != null).toList());

        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("MMTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        // Seed token_payment setting
        if (settingRepository.findByKeyAndBranchId("token_payment", SEED_BRANCH_ID).isEmpty()) {
            Setting s = new Setting();
            s.setBranchId(SEED_BRANCH_ID);
            s.setKey("token_payment");
            s.setValue("test-sckaler-token");
            settingRepository.save(s);
        }

        Invoice inv = new Invoice();
        inv.setBranchId(SEED_BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        inv.setPaid(false);
        inv.setStatus(InvoiceStatus.PENDING);
        invoiceId = invoiceRepository.save(inv).getId();

        // Pre-create a payment with SUCCESS for the anti-doublon test
        Invoice invPaid = new Invoice();
        invPaid.setBranchId(SEED_BRANCH_ID);
        invPaid.setTotal(new BigDecimal("3000.00"));
        invPaid.setPaid(true);
        UUID paidInvoiceId = invoiceRepository.save(invPaid).getId();

        Payment successPayment = new Payment();
        successPayment.setBranchId(SEED_BRANCH_ID);
        successPayment.setInvoice(invPaid);
        successPayment.setPaymentStatus("SUCCESS");
        successPayment.setPaymentName("MOBILEMONEY-MTN");
        successPayment.setPaymentDate(LocalDate.now());
        successPayment.setAmount(BigDecimal.ZERO);
        paymentId = paymentRepository.save(successPayment).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/payments";
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
    @DisplayName("POST /payments/initiate avec mock Sckaler MTN → 200 {message: INITIATED}")
    void initiatePayment_mtn_returns200Initiated() {
        String token = loginAndGetToken();

        mockServer.expect(requestToUriTemplate("https://pay.sckaler.cloud/api/collection/mtn"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\": \"SUCCESS\", \"transaction_id\": \"txn-test-001\", \"msg\": \"OK\"}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> body = Map.of(
                "invoiceId", invoiceId.toString(),
                "phone", "97123456",
                "amount", "5000",
                "provider", "MOBILEMONEY-MTN",
                "fee", "100");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/initiate", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("message")).isEqualTo("INITIATED");
        mockServer.verify();
    }

    @Test
    @DisplayName("POST /payments/initiate avec Payment SUCCESS existant → 422 PAYMENT_ALREADY_SUCCESS")
    void initiatePayment_alreadySuccess_returns422() {
        String token = loginAndGetToken();

        // Use the invoice that already has a SUCCESS payment
        Invoice invWithSuccess = paymentRepository.findById(paymentId).orElseThrow().getInvoice();

        Map<String, Object> body = Map.of(
                "invoiceId", invWithSuccess.getId().toString(),
                "phone", "97000000",
                "amount", "3000",
                "provider", "MOBILEMONEY-MTN",
                "fee", "0");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/initiate", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("GET /payments/{id}/status avec mock Sckaler → 200 {message: SUCCESS}")
    void checkStatus_returns200WithStatus() {
        String token = loginAndGetToken();

        mockServer.expect(requestToUriTemplate("https://pay.sckaler.cloud/api/transaction/status/txn-for-status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\": \"SUCCESS\"}", MediaType.APPLICATION_JSON));

        // Update payment with a paymentId for the status check
        Payment p = paymentRepository.findById(paymentId).orElseThrow();
        p.setPaymentId("txn-for-status");
        paymentRepository.save(p);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + paymentId + "/status", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("message")).isEqualTo("SUCCESS");
        mockServer.verify();
    }
}
