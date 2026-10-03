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
class CashboxDailyControllerIT {

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
    @Autowired private CashboxRepository cashboxRepository;
    @Autowired private CashboxDailyRepository cashboxDailyRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_daily_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID cashboxId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("DailyTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        // Ensure a "vente" cashbox exists
        cashboxRepository.findFirstByBranchIdAndType(SEED_BRANCH_ID, "vente")
                .ifPresentOrElse(
                        c -> cashboxId = c.getId(),
                        () -> {
                            Cashbox c = new Cashbox();
                            c.setBranchId(SEED_BRANCH_ID);
                            c.setName("Caisse vente IT");
                            c.setType("vente");
                            cashboxId = cashboxRepository.save(c).getId();
                        });

        // Clean up dailies to avoid upsert conflicts across tests
        cashboxDailyRepository.deleteAll();
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
    @DisplayName("POST /cashbox-dailies → 201 ouvre la session de caisse")
    void open_returns201() {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of(
                "soldeOuverture", "500.00",
                "cashboxId", cashboxId.toString());

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/cashbox-dailies",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("status")).isEqualTo(1);
        assertThat(response.getBody().data().get("code")).asString().startsWith("OC");
    }

    @Test
    @DisplayName("POST /cashbox-dailies deux fois même jour → upsert, pas de doublon")
    void open_twice_upserts() {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("soldeOuverture", "200.00", "cashboxId", cashboxId.toString());

        restTemplate.exchange("http://localhost:" + port + "/api/v1/cashbox-dailies",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        restTemplate.exchange("http://localhost:" + port + "/api/v1/cashbox-dailies",
                HttpMethod.POST, new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        long count = cashboxDailyRepository.findByBranchId(SEED_BRANCH_ID,
                org.springframework.data.domain.PageRequest.of(0, 100)).getTotalElements();
        assertThat(count).isEqualTo(1); // Un seul enregistrement malgré 2 appels
    }

    @Test
    @DisplayName("GET /cashbox-dailies/summary → 200 avec les totaux")
    void summary_returns200() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/cashbox-dailies/summary",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("totalEspeces");
    }
}
