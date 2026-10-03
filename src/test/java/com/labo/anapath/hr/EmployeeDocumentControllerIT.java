package com.labo.anapath.hr;

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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EmployeeDocumentControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo")
            .withUsername("test")
            .withPassword("test");

    static Path tempStorageDir;

    static {
        try {
            tempStorageDir = Files.createTempDirectory("labo-doc-it-");
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
    @Autowired private EmployeeRepository employeeRepository;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL    = "admin_doc_it@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID employeeId;

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));
            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("DocTest");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        Employee emp = new Employee();
        emp.setBranchId(SEED_BRANCH_ID);
        emp.setFirstName("Roméo");
        emp.setLastName("Agossou");
        emp.setSalary(new BigDecimal("300000"));
        employeeId = employeeRepository.save(emp).getId();
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

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/employee-documents";
    }

    private String uploadDocWithFile(String token) {
        HttpHeaders headers = multipartHeaders(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("employeeId", employeeId.toString());
        body.add("name", "Contrat CDI");
        body.add("file", new ByteArrayResource("pdf content".getBytes()) {
            @Override public String getFilename() { return "contrat.pdf"; }
        });
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().data().get("id").toString();
    }

    @Test
    @DisplayName("POST /employee-documents avec fichier → 201, taille du fichier renseignée")
    void upload_withFile_returns201_andFilePathSet() {
        String token = loginAndGetToken();

        HttpHeaders headers = multipartHeaders(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("employeeId", employeeId.toString());
        body.add("name", "Diplôme BAC");
        body.add("type", "diplome");
        body.add("file", new ByteArrayResource("fake pdf".getBytes()) {
            @Override public String getFilename() { return "bac.pdf"; }
        });

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Le chemin de stockage ne sort plus dans le JSON (@JsonIgnore) : seule la taille en témoigne.
        assertThat(response.getBody().data().get("fileSize")).isNotNull();
    }

    @Test
    @DisplayName("POST /employee-documents sans fichier → 201 filePath null")
    void upload_withoutFile_returns201_andFilePathNull() {
        String token = loginAndGetToken();

        HttpHeaders headers = multipartHeaders(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("employeeId", employeeId.toString());
        body.add("name", "Note interne");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("filePath")).isNull();
    }

    @Test
    @DisplayName("GET /employee-documents?employeeId=... → 200 liste paginée")
    void findAll_byEmployeeId_returns200() {
        String token = loginAndGetToken();
        uploadDocWithFile(token);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "?employeeId=" + employeeId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("content");
    }

    @Test
    @DisplayName("GET /employee-documents/{id} → 200")
    void findById_returns200() {
        String token = loginAndGetToken();
        String docId = uploadDocWithFile(token);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + docId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("id")).isEqualTo(docId);
    }

    @Test
    @DisplayName("GET /employee-documents/{unknownId} → 404")
    void findById_unknownId_returns404() {
        String token = loginAndGetToken();

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUT /employee-documents/{id} → 200 met à jour name et type")
    void update_returns200_andUpdatesMetadata() {
        String token = loginAndGetToken();
        String docId = uploadDocWithFile(token);

        Map<String, String> updateBody = Map.of("name", "Contrat CDD mis à jour", "type", "contrat");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl() + "/" + docId,
                HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("name")).isEqualTo("Contrat CDD mis à jour");
        assertThat(response.getBody().data().get("type")).isEqualTo("contrat");
    }

    @Test
    @DisplayName("DELETE /employee-documents/{id} → 200 puis GET → 404")
    void delete_returns200_thenGet_returns404() {
        String token = loginAndGetToken();
        String docId = uploadDocWithFile(token);

        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                baseUrl() + "/" + docId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<Void>> getResponse = restTemplate.exchange(
                baseUrl() + "/" + docId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /employee-documents/{id}/download → 200 Content-Disposition attachment")
    void download_existingFile_returnsResource() {
        String token = loginAndGetToken();
        String docId = uploadDocWithFile(token);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl() + "/" + docId + "/download",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment");
    }

    @Test
    @DisplayName("POST /employee-documents sans token → 401")
    void upload_withoutToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("employeeId", employeeId.toString());
        body.add("name", "Doc sans auth");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl(), HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
