package com.labo.anapath.role;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
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
class RoleIntegrationTest {

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
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_role_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("Role Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/roles";
    }

    private String loginAndGetToken(String email, String password) {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(email);
    }

    @Test
    @DisplayName("POST /roles - crée un rôle avec slug auto-généré → 201")
    void createRole_returns201WithAutoSlug() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Technicien Test");

        ResponseEntity<ApiResponse<RoleResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RoleResponseDto created = response.getBody().data();
        assertThat(created.name()).isEqualTo("Technicien Test");
        assertThat(created.slug()).isEqualTo("technicien-test");

        // Cleanup
        roleRepository.findById(created.id()).ifPresent(roleRepository::delete);
    }

    @Test
    @DisplayName("POST /roles - doublon de slug → 409 Conflict")
    void createRole_duplicateSlug_returns409() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Admin"); // "admin" slug already seeded

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("GET /roles - retourne la liste paginée des rôles → 200")
    void getRoles_returnsPaginatedList() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<PageResponse<RoleResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isNotEmpty();
    }

    @Test
    @DisplayName("GET /permissions - retourne la liste complète des permissions → 200")
    void getPermissions_returnsFullList() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<List<PermissionResponseDto>>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/permissions",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotEmpty();
    }

    @Test
    @DisplayName("POST /roles/{id}/permissions - remplace les permissions → 200")
    void assignPermissions_replacesPermissions() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        // Create a role to test with
        Role testRole = new Role();
        testRole.setBranchId(SEED_BRANCH_ID);
        testRole.setName("Role Perm Test");
        testRole.setSlug("role-perm-test-" + UUID.randomUUID().toString().substring(0, 8));
        Role savedRole = roleRepository.save(testRole);

        Permission viewPerm = permissionRepository.findBySlug("view-patients")
                .orElseThrow(() -> new IllegalStateException("view-patients not seeded"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        List<UUID> permIds = List.of(viewPerm.getId());

        ResponseEntity<ApiResponse<RoleResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + savedRole.getId() + "/permissions",
                HttpMethod.POST,
                new HttpEntity<>(permIds, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().permissions()).hasSize(1);
        assertThat(response.getBody().data().permissions().get(0).slug()).isEqualTo("view-patients");

        // Cleanup
        roleRepository.delete(savedRole);
    }

    @Test
    @DisplayName("RBAC - user avec view-patients → GET /patients → 200")
    void rbac_userWithViewPatients_canGetPatients() {
        // Create a role with only view-patients
        Permission viewPerm = permissionRepository.findBySlug("view-patients")
                .orElseThrow(() -> new IllegalStateException("view-patients not seeded"));

        Role rbacRole = new Role();
        rbacRole.setBranchId(SEED_BRANCH_ID);
        rbacRole.setName("RBAC View Test");
        rbacRole.setSlug("rbac-view-test-" + UUID.randomUUID().toString().substring(0, 8));
        rbacRole.setPermissions(List.of(viewPerm));
        Role savedRole = roleRepository.save(rbacRole);

        User rbacUser = new User();
        rbacUser.setBranchId(SEED_BRANCH_ID);
        rbacUser.setFirstname("RBAC");
        rbacUser.setLastname("View User");
        rbacUser.setEmail("rbac_view_" + UUID.randomUUID().toString().substring(0, 8) + "@labo.bj");
        rbacUser.setPassword(passwordEncoder.encode("rbacPass123"));
        rbacUser.setActive(true);
        rbacUser.setRoles(List.of(savedRole));
        User savedUser = userRepository.save(rbacUser);

        String token = loginAndGetToken(savedUser.getEmail(), "rbacPass123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/patients?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Cleanup
        userRepository.delete(savedUser);
        roleRepository.delete(savedRole);
    }

    @Test
    @DisplayName("RBAC - user sans create-patients → POST /patients → 403")
    void rbac_userWithoutCreatePatients_cannotPostPatients() {
        Permission viewPerm = permissionRepository.findBySlug("view-patients")
                .orElseThrow(() -> new IllegalStateException("view-patients not seeded"));

        Role rbacRole = new Role();
        rbacRole.setBranchId(SEED_BRANCH_ID);
        rbacRole.setName("RBAC No Create Test");
        rbacRole.setSlug("rbac-no-create-" + UUID.randomUUID().toString().substring(0, 8));
        rbacRole.setPermissions(List.of(viewPerm)); // only view, not create
        Role savedRole = roleRepository.save(rbacRole);

        User rbacUser = new User();
        rbacUser.setBranchId(SEED_BRANCH_ID);
        rbacUser.setFirstname("RBAC");
        rbacUser.setLastname("No Create User");
        rbacUser.setEmail("rbac_nocreate_" + UUID.randomUUID().toString().substring(0, 8) + "@labo.bj");
        rbacUser.setPassword(passwordEncoder.encode("rbacPass123"));
        rbacUser.setActive(true);
        rbacUser.setRoles(List.of(savedRole));
        User savedUser = userRepository.save(rbacUser);

        String token = loginAndGetToken(savedUser.getEmail(), "rbacPass123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Content-Type", "application/json");

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>("{\"code\":\"P-RBAC-001\",\"firstname\":\"Rbac\",\"lastname\":\"Test\"}", headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Cleanup
        userRepository.delete(savedUser);
        roleRepository.delete(savedRole);
    }

    @Test
    @DisplayName("PUT /roles/{id} sans permissionIds → permissions existantes préservées (bug fix)")
    void updateRole_withoutPermissionIds_preservesPermissions() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        Permission viewPerm = permissionRepository.findBySlug("view-patients")
                .orElseThrow(() -> new IllegalStateException("view-patients not seeded"));

        // Créer un rôle avec une permission
        Role role = new Role();
        role.setBranchId(SEED_BRANCH_ID);
        role.setName("Role Preserve Perms Test");
        role.setSlug("role-preserve-" + UUID.randomUUID().toString().substring(0, 8));
        role.setPermissions(List.of(viewPerm));
        Role saved = roleRepository.save(role);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // PUT sans permissionIds (null → ne doit pas effacer les permissions)
        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Role Preserve Perms Updated");
        // permissionIds non défini → null

        ResponseEntity<ApiResponse<RoleResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().permissions())
                .as("Les permissions ne doivent pas être effacées quand permissionIds est absent")
                .hasSize(1);
        assertThat(response.getBody().data().permissions().get(0).slug()).isEqualTo("view-patients");

        // Cleanup
        roleRepository.findById(saved.getId()).ifPresent(roleRepository::delete);
    }

    @Test
    @DisplayName("PUT /roles/{id} avec permissionIds vide → efface toutes les permissions")
    void updateRole_withEmptyPermissionIds_clearsPermissions() {
        String token = loginAndGetToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        Permission viewPerm = permissionRepository.findBySlug("view-patients")
                .orElseThrow(() -> new IllegalStateException("view-patients not seeded"));

        // Créer un rôle avec une permission
        Role role = new Role();
        role.setBranchId(SEED_BRANCH_ID);
        role.setName("Role Clear Perms Test");
        role.setSlug("role-clear-" + UUID.randomUUID().toString().substring(0, 8));
        role.setPermissions(List.of(viewPerm));
        Role saved = roleRepository.save(role);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // PUT avec permissionIds = [] → efface toutes les permissions
        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Role Clear Perms Updated");
        dto.setPermissionIds(List.of()); // vide explicitement

        ResponseEntity<ApiResponse<RoleResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().permissions())
                .as("Les permissions doivent être effacées quand permissionIds = []")
                .isEmpty();

        // Cleanup
        roleRepository.findById(saved.getId()).ifPresent(roleRepository::delete);
    }

    @Test
    @DisplayName("GET /roles - sans token → 401")
    void getRoles_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
