package com.labo.anapath.user;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
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
class UserIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Autowired private Jetons jetons;

    // Branch ID seeded in V2 migration
    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_user_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("User Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/users";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("GET /users - retourne une liste paginée pour la branche courante")
    void findAll_returnsPaginatedList() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<PageResponse<UserResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isNotEmpty();
    }

    @Test
    @DisplayName("POST /users - crée un utilisateur avec les rôles assignés")
    void create_withRoles_returns201() {
        String token = loginAndGetToken();
        Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID).orElseThrow();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Content-Type", "application/json");

        UserRequestDto dto = new UserRequestDto();
        dto.setFirstname("Nouveau");
        dto.setLastname("Technicien");
        dto.setEmail("technicien_new@labo.bj");
        dto.setPassword("Vq7-tz9Lp2-Xw4");
        dto.setRoleIds(List.of(adminRole.getId()));

        ResponseEntity<ApiResponse<UserResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponseDto created = response.getBody().data();
        assertThat(created.email()).isEqualTo("technicien_new@labo.bj");
        assertThat(created.roles()).isNotEmpty();

        // Cleanup
        userRepository.findByEmail("technicien_new@labo.bj").ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("POST /users - email en double → 409 Conflict")
    void create_duplicateEmail_returns409() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        UserRequestDto dto = new UserRequestDto();
        dto.setFirstname("Duplicate");
        dto.setLastname("User");
        dto.setEmail(ADMIN_EMAIL); // email déjà utilisé
        dto.setPassword("Vq7-tz9Lp2-Xw4");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("PATCH /users/{id}/status - désactiver reset is_connect et two_factor_enabled")
    void toggleStatus_deactivate_resetsFields() {
        String token = loginAndGetToken();

        // Créer un utilisateur actif avec is_connect=true
        User target = new User();
        target.setBranchId(SEED_BRANCH_ID);
        target.setFirstname("Toggle");
        target.setLastname("Target");
        target.setEmail("toggle_target@labo.bj");
        target.setPassword(passwordEncoder.encode("pass1234"));
        target.setActive(true);
        target.setConnect(true);
        target.setTwoFactorEnabled(false);
        User saved = userRepository.save(target);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User updated = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.isConnect()).isFalse();

        // Cleanup
        userRepository.delete(updated);
    }

    @Test
    @DisplayName("PATCH /users/{id}/status - réactiver → is_active=true")
    void toggleStatus_activate_setsActiveTrue() {
        String token = loginAndGetToken();

        User target = new User();
        target.setBranchId(SEED_BRANCH_ID);
        target.setFirstname("Inactive");
        target.setLastname("Target");
        target.setEmail("inactive_target@labo.bj");
        target.setPassword(passwordEncoder.encode("pass1234"));
        target.setActive(false);
        User saved = userRepository.save(target);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User updated = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.isActive()).isTrue();

        // Cleanup
        userRepository.delete(updated);
    }

    @Test
    @DisplayName("PATCH /users/{id}/password - bon mot de passe → 200")
    void updatePassword_correctOldPassword_returns200() {
        String token = loginAndGetToken();
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword(ADMIN_PASSWORD);
        request.setNewPassword("Rk3-mb8Qz5-Hn6");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + admin.getId() + "/password",
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Remettre l'ancien mot de passe pour ne pas casser les autres tests
        User updated = userRepository.findById(admin.getId()).orElseThrow();
        updated.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        userRepository.save(updated);
    }

    @Test
    @DisplayName("PATCH /users/{id}/password - mauvais mot de passe → 422")
    void updatePassword_wrongOldPassword_returns422() {
        String token = loginAndGetToken();
        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("wrongPassword999");
        request.setNewPassword("Rk3-mb8Qz5-Hn6");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + admin.getId() + "/password",
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("GET /users - sans token → 401")
    void findAll_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GET /users - token invalide → 401 ou 403 (AC-7)")
    void findAll_invalidToken_returns401or403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid.jwt.token");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode().value())
                .as("Un token invalide doit retourner 401 ou 403")
                .isIn(401, 403);
    }

    @Test
    @DisplayName("PUT /users/{id} - isActive absent dans le body → statut inchangé (AC bug fix)")
    void update_withoutIsActive_doesNotChangeActiveStatus() {
        String token = loginAndGetToken();

        // Créer un utilisateur inactif
        User target = new User();
        target.setBranchId(SEED_BRANCH_ID);
        target.setFirstname("Inactive");
        target.setLastname("For Update");
        target.setEmail("inactive_update@labo.bj");
        target.setPassword(passwordEncoder.encode("pass1234"));
        target.setActive(false);
        User saved = userRepository.save(target);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // Envoyer un PUT sans champ isActive
        UserRequestDto dto = new UserRequestDto();
        dto.setFirstname("Updated");
        dto.setLastname("Name");
        dto.setEmail("inactive_update@labo.bj");

        ResponseEntity<ApiResponse<UserResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User updated = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.isActive())
                .as("isActive doit rester false puisqu'il n'a pas été envoyé")
                .isFalse();

        // Cleanup
        userRepository.delete(updated);
    }
}
