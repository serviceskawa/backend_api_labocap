package com.labo.anapath.doctor;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class HospitalIntegrationTest {

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

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_hospital_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("Hospital Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/hospitals";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("POST /hospitals - crée un hôpital → 201")
    void createHospital_returns201() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HospitalRequestDto dto = new HospitalRequestDto();
        dto.setName("Clinique Test " + UUID.randomUUID().toString().substring(0, 8));
        dto.setTelephone("+229 23456789");
        dto.setAdresse("Rue de la Paix");
        dto.setEmail("clinique@test.bj");
        dto.setCommission(5.0);

        ResponseEntity<ApiResponse<HospitalResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().name()).startsWith("Clinique Test");

        // Cleanup
        hospitalRepository.findById(response.getBody().data().id())
                .ifPresent(hospitalRepository::delete);
    }

    @Test
    @DisplayName("POST /hospitals - doublon de nom → 409 Conflict")
    void createHospital_duplicateName_returns409() {
        String token = loginAndGetToken();

        Hospital seed = new Hospital();
        seed.setName("Clinique Doublon Test");
        seed.setBranchId(SEED_BRANCH_ID);
        Hospital saved = hospitalRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            HospitalRequestDto dto = new HospitalRequestDto();
            dto.setName("clinique doublon test");
            dto.setTelephone("+229 00000001");

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            hospitalRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /hospitals/search?q=CHU → 200, contient CHU")
    void searchHospitals_returnsChuResults() {
        String token = loginAndGetToken();

        Hospital seed = new Hospital();
        seed.setName("CHU de Cotonou");
        seed.setBranchId(SEED_BRANCH_ID);
        Hospital saved = hospitalRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<List<HospitalResponseDto>>> response = restTemplate.exchange(
                    baseUrl() + "/search?q=CHU",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isNotEmpty();
            assertThat(response.getBody().data().stream()
                    .anyMatch(h -> h.name().contains("CHU"))).isTrue();
        } finally {
            hospitalRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("DELETE /hospitals/{id} - suppression sans contrainte → 200")
    void deleteHospital_returns200() {
        String token = loginAndGetToken();

        Hospital hospital = new Hospital();
        hospital.setName("Hopital Delete Test " + UUID.randomUUID().toString().substring(0, 8));
        hospital.setBranchId(SEED_BRANCH_ID);
        Hospital saved = hospitalRepository.save(hospital);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hospitalRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /hospitals/search?q=<terme inexistant> → 200 liste vide")
    void searchHospitals_noMatch_returnsEmptyList() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        String uniqueQuery = "HOPITAL_INEXISTANT_" + UUID.randomUUID().toString().replace("-", "");

        ResponseEntity<ApiResponse<List<HospitalResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "/search?q=" + uniqueQuery,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
    }

    @Test
    @DisplayName("GET /hospitals - sans token → 401")
    void getHospitals_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
