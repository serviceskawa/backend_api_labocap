package com.labo.anapath.branch;

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
import org.springframework.boot.test.web.client.TestRestTemplate;
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
class BranchIntegrationTest {

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
    private BranchRepository branchRepository;

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
    private static final String ADMIN_EMAIL = "admin_branch_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("Branch Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/branches";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("POST /branches - crée une agence → 201")
    void createBranch_returns201() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        BranchRequestDto dto = new BranchRequestDto();
        dto.setName("Agence Test Parakou");
        dto.setCode("PARAKOU-001");
        dto.setLocation("Parakou");

        ResponseEntity<ApiResponse<BranchResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().name()).isEqualTo("Agence Test Parakou");

        // Cleanup
        branchRepository.findById(response.getBody().data().id())
                .ifPresent(branchRepository::delete);
    }

    @Test
    @DisplayName("POST /branches - doublon de nom → 409 Conflict")
    void createBranch_duplicateName_returns409() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // "Siège" is already seeded in V2
        BranchRequestDto dto = new BranchRequestDto();
        dto.setName("Siège");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("GET /branches - retourne la liste paginée → 200")
    void getBranches_returnsPaginatedList() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<PageResponse<BranchResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isNotEmpty();
    }

    @Test
    @DisplayName("DELETE /branches/{id} - branche avec utilisateurs liés → 422")
    void deleteBranch_withLinkedUsers_returns422() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // SEED_BRANCH_ID has the admin user linked — safe to use without creating data
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + SEED_BRANCH_ID,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("DELETE /branches/{id} - branche sans utilisateurs → 200 soft delete")
    void deleteBranch_noLinkedUsers_returns200() {
        String token = loginAndGetToken();

        // Create a branch with no users
        Branch emptyBranch = new Branch();
        emptyBranch.setName("Agence To Delete " + UUID.randomUUID().toString().substring(0, 8));
        Branch saved = branchRepository.save(emptyBranch);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(branchRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /branches - sans token → 401")
    void getBranches_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /branches/{id} - retourne la branche → 200")
    void getBranchById_returns200() {
        String token = loginAndGetToken();

        Branch seed = new Branch();
        seed.setName("Branche Get Test " + UUID.randomUUID().toString().substring(0, 6));
        seed.setCode("BGT");
        Branch saved = branchRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<BranchResponseDto>> response = restTemplate.exchange(
                    baseUrl() + "/" + saved.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(saved.getId());
        } finally {
            branchRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("PUT /branches/{id} - mise à jour → 200")
    void updateBranch_returns200() {
        String token = loginAndGetToken();

        Branch seed = new Branch();
        seed.setName("Branche Update Test " + UUID.randomUUID().toString().substring(0, 6));
        Branch saved = branchRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            BranchRequestDto dto = new BranchRequestDto();
            dto.setName("Branche Updated " + UUID.randomUUID().toString().substring(0, 6));

            ResponseEntity<ApiResponse<BranchResponseDto>> response = restTemplate.exchange(
                    baseUrl() + "/" + saved.getId(),
                    HttpMethod.PUT,
                    new HttpEntity<>(dto, headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).startsWith("Branche Updated");
        } finally {
            branchRepository.delete(saved);
        }
    }
}
