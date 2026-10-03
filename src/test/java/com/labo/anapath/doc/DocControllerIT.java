package com.labo.anapath.doc;

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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DocControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo")
            .withUsername("test")
            .withPassword("test");

    static Path tempStorageDir;

    static {
        try {
            tempStorageDir = Files.createTempDirectory("labo-doc-ctrl-it-");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("app.storage.path", tempStorageDir::toString);
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_doc_ctrl_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("DocCtrl");
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

    private HttpHeaders multipartHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return h;
    }

    private String docsUrl() {
        return "http://localhost:" + port + "/api/v1/docs";
    }

    private String categoriesUrl() {
        return "http://localhost:" + port + "/api/v1/documentation-categories";
    }

    private String createDoc(String token, String title) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", title);
        body.add("file", new ByteArrayResource("pdf content".getBytes()) {
            @Override public String getFilename() { return "doc.pdf"; }
        });
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                docsUrl(), HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().data().get("id").toString();
    }

    @Test
    @DisplayName("POST /docs avec fichier → 201 titre et attachment renseignés")
    void create_withFile_returns201() {
        String token = loginAndGetToken();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Procédure standard de biopsie");
        body.add("file", new ByteArrayResource("fake pdf bytes".getBytes()) {
            @Override public String getFilename() { return "procedure.pdf"; }
        });

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                docsUrl(), HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("title")).isEqualTo("Procédure standard de biopsie");
        assertThat(response.getBody().data().get("attachment")).isNotNull();
        assertThat(response.getBody().data().get("fileSize")).isNotNull();
    }

    @Test
    @DisplayName("POST /docs/{id}/versions → 201 version == 2")
    void addVersion_returns201_versionIncremented() {
        String token = loginAndGetToken();
        String docId = createDoc(token, "Guide protocole");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("v2 pdf bytes".getBytes()) {
            @Override public String getFilename() { return "guide_v2.pdf"; }
        });

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                docsUrl() + "/" + docId + "/versions",
                HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("version")).isEqualTo(2);
        assertThat(response.getBody().data().get("attachment")).isNotNull();
    }

    @Test
    @DisplayName("GET /docs/{id}/versions → 200 liste triée par version croissante")
    void getVersions_returns200_sortedAsc() {
        String token = loginAndGetToken();
        String docId = createDoc(token, "Manuel qualité");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("v2 bytes".getBytes()) {
            @Override public String getFilename() { return "manuel_v2.pdf"; }
        });
        restTemplate.exchange(docsUrl() + "/" + docId + "/versions",
                HttpMethod.POST, new HttpEntity<>(body, multipartHeaders(token)),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {});

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                docsUrl() + "/" + docId + "/versions",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> versions = response.getBody().data();
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("version")).isEqualTo(1);
        assertThat(versions.get(1).get("version")).isEqualTo(2);
    }

    @Test
    @DisplayName("DELETE /docs/{id} → 200 puis GET → 404 (soft-delete)")
    void delete_returns200_thenGet_returns404() {
        String token = loginAndGetToken();
        String docId = createDoc(token, "Document à supprimer");

        ResponseEntity<ApiResponse<Void>> deleteResp = restTemplate.exchange(
                docsUrl() + "/" + docId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<Void>> getResp = restTemplate.exchange(
                docsUrl() + "/" + docId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST /documentation-categories → 201 avec nom")
    void createCategory_returns201() {
        String token = loginAndGetToken();

        Map<String, String> body = Map.of("name", "Protocoles cliniques");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                categoriesUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("name")).isEqualTo("Protocoles cliniques");
        assertThat(response.getBody().data().get("branchId")).isNotNull();
    }

    @Test
    @DisplayName("POST /docs sans token → 401")
    void create_withoutToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("title", "Doc sans auth");
        body.add("file", new ByteArrayResource("bytes".getBytes()) {
            @Override public String getFilename() { return "test.pdf"; }
        });

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                docsUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
