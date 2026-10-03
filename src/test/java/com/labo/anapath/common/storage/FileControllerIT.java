package com.labo.anapath.common.storage;

import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FileControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo")
            .withUsername("test")
            .withPassword("test");

    static Path tempStorageDir;

    static {
        try {
            tempStorageDir = Files.createTempDirectory("labo-files-it-");
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
    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Jetons jetons;
    @Autowired private com.labo.anapath.role.RoleRepository roleRepository;
    @Autowired private FichierStockeRepository fichiers;

    private static final UUID AGENCE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String EMAIL = "fichiers_it@labo.bj";

    // Les fichiers ne sont plus publics : il faut une session ouverte.
    @BeforeEach
    void seedUser() {
        if (userRepository.findByEmail(EMAIL).isEmpty()) {
            User user = new User();
            user.setBranchId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            user.setFirstname("Fichiers");
            user.setLastname("Test");
            user.setEmail(EMAIL);
            user.setPassword(passwordEncoder.encode("fichiersPass123"));
            user.setActive(true);
            // Admin du jeu de données : le fichier n'est servi qu'à qui peut
            // lire l'entité qui le possède.
            user.setRoles(java.util.List.of(roleRepository.findBySlugAndBranchId("admin", AGENCE).orElseThrow()));
            userRepository.save(user);
        }
    }

    private HttpEntity<Void> connecte() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jetons.pour(EMAIL));
        return new HttpEntity<>(headers);
    }

    @Test
    @DisplayName("GET /files/{relativePath} fichier existant → 200 avec Content-Type")
    void getFile_existing_returns200() throws IOException {
        Path docDir = tempStorageDir.resolve("documents");
        Files.createDirectories(docDir);
        String uuid = UUID.randomUUID().toString();
        Path file = docDir.resolve(uuid + ".pdf");
        Files.write(file, "PDF content".getBytes());
        fichiers.rattacher("documents/" + uuid + ".pdf", FichierStocke.DOC, UUID.randomUUID(), AGENCE);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/files/documents/" + uuid + ".pdf",
                HttpMethod.GET, connecte(), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
    }

    @Test
    @DisplayName("GET /files/{relativePath} UUID inexistant → 404")
    void getFile_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/files/documents/" + UUID.randomUUID() + ".pdf",
                HttpMethod.GET, connecte(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /files/{relativePath} sans jeton → 401 (les fichiers ne sont pas publics)")
    void getFile_noToken_returns401() throws IOException {
        Path docDir = tempStorageDir.resolve("documents");
        Files.createDirectories(docDir);
        String uuid = UUID.randomUUID().toString();
        Files.write(docDir.resolve(uuid + ".png"), "PNG".getBytes());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/files/documents/" + uuid + ".png",
                HttpMethod.GET, HttpEntity.EMPTY, byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
