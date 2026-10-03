package com.labo.anapath.client;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.contract.Contrat;
import com.labo.anapath.contract.ContratRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClientIntegrationTest {

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
    private ClientRepository clientRepository;

    @Autowired
    private ContratRepository contratRepository;

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
    private static final String ADMIN_EMAIL = "admin_client_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("Client Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/clients";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("POST /clients - crée un client → 201")
    void createClient_returns201() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        ClientRequestDto dto = new ClientRequestDto();
        dto.setName("Clinique Test " + uniqueSuffix);
        dto.setContact("22912345678");

        ResponseEntity<ApiResponse<ClientResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().name()).startsWith("Clinique Test");

        // Cleanup
        clientRepository.findById(response.getBody().data().id())
                .ifPresent(clientRepository::delete);
    }

    @Test
    @DisplayName("POST /clients - doublon de nom (casse différente) → 409 Conflict")
    void createClient_duplicateName_returns409() {
        String token = loginAndGetToken();

        // Seed a client
        Client seed = new Client();
        seed.setName("Clinique Doublon Test");
        seed.setBranchId(SEED_BRANCH_ID);
        Client saved = clientRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ClientRequestDto dto = new ClientRequestDto();
            dto.setName("clinique doublon test"); // casse différente
            dto.setContact("22912345678");

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            clientRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("POST /clients - doublon d'IFU → 409 Conflict")
    void createClient_duplicateIfu_returns409() {
        String token = loginAndGetToken();

        // Seed a client with IFU
        Client seed = new Client();
        seed.setName("Client IFU Test " + UUID.randomUUID().toString().substring(0, 8));
        seed.setIfu("1234567890123");
        seed.setBranchId(SEED_BRANCH_ID);
        Client saved = clientRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ClientRequestDto dto = new ClientRequestDto();
            dto.setName("Autre Client " + UUID.randomUUID().toString().substring(0, 8));
            dto.setIfu("1234567890123"); // même IFU
            dto.setContact("22912345678");

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            clientRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /clients/search?q=Clinique → 200, contient le client seedé")
    void searchClients_returnsResults() {
        String token = loginAndGetToken();

        // Seed a client
        Client seed = new Client();
        seed.setName("Clinique Recherche");
        seed.setBranchId(SEED_BRANCH_ID);
        Client saved = clientRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<List<ClientResponseDto>>> response = restTemplate.exchange(
                    baseUrl() + "/search?q=Clinique",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).isNotEmpty();
            assertThat(response.getBody().data().stream()
                    .anyMatch(c -> c.name().contains("Clinique"))).isTrue();
        } finally {
            clientRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("DELETE /clients/{id} - client avec contrat lié → 422")
    void deleteClient_withLinkedContrat_returns422() {
        String token = loginAndGetToken();

        // Create client
        Client client = new Client();
        client.setName("Client Avec Contrat " + UUID.randomUUID().toString().substring(0, 8));
        client.setBranchId(SEED_BRANCH_ID);
        Client savedClient = clientRepository.save(client);

        // Create contrat linked to client
        Contrat contrat = new Contrat();
        contrat.setBranchId(SEED_BRANCH_ID);
        contrat.setClientId(savedClient.getId());
        contrat.setNbrTests(10);
        contrat.setStartDate(LocalDate.now());
        Contrat savedContrat = contratRepository.save(contrat);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl() + "/" + savedClient.getId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            contratRepository.delete(savedContrat);
            clientRepository.delete(savedClient);
        }
    }

    @Test
    @DisplayName("DELETE /clients/{id} - client sans contrat → 200 soft delete")
    void deleteClient_noLinkedContrat_returns200() {
        String token = loginAndGetToken();

        Client client = new Client();
        client.setName("Client Vide " + UUID.randomUUID().toString().substring(0, 8));
        client.setBranchId(SEED_BRANCH_ID);
        Client saved = clientRepository.save(client);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clientRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /clients?page=0&size=20 → 200 page de clients (AC-4)")
    void listClients_returnsPagedResult() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<PageResponse<ClientResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().page()).isEqualTo(0);
        assertThat(response.getBody().data().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("GET /clients/{id} → 200 avec le détail du client (AC-6)")
    void getClientById_returnsClient() {
        String token = loginAndGetToken();

        // Seed a client
        Client seed = new Client();
        seed.setName("Client Get Test " + UUID.randomUUID().toString().substring(0, 8));
        seed.setBranchId(SEED_BRANCH_ID);
        Client saved = clientRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<ClientResponseDto>> response = restTemplate.exchange(
                    baseUrl() + "/" + saved.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(saved.getId());
            assertThat(response.getBody().data().name()).isEqualTo(saved.getName());
        } finally {
            clientRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /clients - sans token → 401")
    void getClients_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
