package com.labo.anapath.testorder;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.contract.Contrat;
import com.labo.anapath.contract.ContratRepository;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
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

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class TestOrderIntegrationTest {

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
    @Autowired private TestOrderRepository testOrderRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ContratRepository contratRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_testorder_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    private UUID seededPatientId;

    @AfterEach
    void cleanup() {
        if (seededPatientId != null) {
            patientRepository.findById(seededPatientId).ifPresent(patientRepository::delete);
            seededPatientId = null;
        }
    }

    @BeforeEach
    void setup() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("TestOrder Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }

        Patient patient = new Patient();
        patient.setBranchId(SEED_BRANCH_ID);
        patient.setFirstname("Koffi");
        patient.setLastname("Adama");
        patient.setAge(30);
        Patient saved = patientRepository.save(patient);
        seededPatientId = saved.getId();
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/test-orders";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("POST /test-orders - crée un bon avec code=null et status=PENDING → 201")
    void createTestOrder_returns201_withNullCode() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        TestOrderRequestDto dto = new TestOrderRequestDto();
        dto.setPatientId(seededPatientId);
        dto.setPrelevementDate(LocalDate.now());

        ResponseEntity<ApiResponse<TestOrderResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().code()).isNull();
        assertThat(response.getBody().data().status()).isEqualTo(TestOrderStatus.PENDING);

        testOrderRepository.findById(response.getBody().data().id())
                .ifPresent(testOrderRepository::delete);
    }

    @Test
    @DisplayName("GET /test-orders - liste paginée → 200")
    void getTestOrders_returnsPagedResults() {
        String token = loginAndGetToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<PageResponse<TestOrderResponseDto>>> response = restTemplate.exchange(
                baseUrl() + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /test-orders/{id} - supprime le bon → 200")
    void deleteTestOrder_returns200() {
        String token = loginAndGetToken();

        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testOrderRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /test-orders - sans token → 401")
    void getTestOrders_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /test-orders/{id}/assign-doctor - assigne un médecin → 200 avec attribuateDoctorId non null")
    void assignDoctor_returns200_withDoctorId() {
        String token = loginAndGetToken();

        UUID doctorId = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Admin user not found"))
                .getId();

        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        AssignDoctorRequestDto dto = new AssignDoctorRequestDto(doctorId);

        ResponseEntity<ApiResponse<TestOrderResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/assign-doctor",
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().attribuateDoctorId()).isEqualTo(doctorId);
        assertThat(response.getBody().data().assignedToUserId()).isEqualTo(doctorId);

        testOrderRepository.findById(saved.getId()).ifPresent(testOrderRepository::delete);
    }

    @Test
    @DisplayName("POST /test-orders/{id}/deliver - livre un bon VALIDATED → 200 avec status DELIVERED")
    void deliverOrder_returns200_withDeliveredStatus() {
        String token = loginAndGetToken();

        Contrat contrat = new Contrat();
        contrat.setBranchId(SEED_BRANCH_ID);
        contrat.setInvoiceUnique(false);
        contrat.setNbrTests(10);
        contrat.setStartDate(LocalDate.now());
        Contrat savedContrat = contratRepository.save(contrat);

        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        seed.setContrat(savedContrat);
        seed.setSubtotal(30000.0);
        seed.setDiscount(0.0);
        seed.setTotal(30000.0);
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // D'abord valider (crée le Report automatiquement)
        restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/status?status=VALIDATED",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<TestOrderResponseDto>>() {});

        // Puis livrer
        ResponseEntity<ApiResponse<TestOrderResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/deliver",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo(TestOrderStatus.DELIVERED);

        // Vérifier que le Report est aussi marqué isDelivered
        reportRepository.findByTestOrderId(saved.getId()).ifPresent(report ->
                assertThat(report.isDelivered()).isTrue());

        // Cleanup
        testOrderRepository.findById(saved.getId()).ifPresent(testOrderRepository::delete);
        contratRepository.delete(savedContrat);
    }

    @Test
    @DisplayName("PATCH /test-orders/{id}/status?status=VALIDATED - valide un bon → 200 avec code non null")
    void validateTestOrder_returns200_withCode() {
        String token = loginAndGetToken();

        // Contrat individuel (invoice_unique=false)
        Contrat contrat = new Contrat();
        contrat.setBranchId(SEED_BRANCH_ID);
        contrat.setInvoiceUnique(false);
        contrat.setNbrTests(10);
        contrat.setStartDate(LocalDate.now());
        Contrat savedContrat = contratRepository.save(contrat);

        // Bon d'examen referençant ce contrat
        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        seed.setContrat(savedContrat);
        seed.setSubtotal(50000.0);
        seed.setDiscount(0.0);
        seed.setTotal(50000.0);
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<TestOrderResponseDto>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/status?status=VALIDATED",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotNull();
        assertThat(response.getBody().data().code()).isNotNull();
        assertThat(response.getBody().data().status()).isEqualTo(TestOrderStatus.VALIDATED);

        // Cleanup
        testOrderRepository.findById(saved.getId()).ifPresent(testOrderRepository::delete);
        contratRepository.delete(savedContrat);
    }

    @Test
    @DisplayName("POST /test-orders/{id}/images - upload multipart → 200 avec liste fichiers")
    void uploadImages_returns200_withFilenameList() {
        String token = loginAndGetToken();

        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Signature PNG : le serveur lit les octets, pas l'extension.
        ByteArrayResource fakeImage = new ByteArrayResource(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}) {
            @Override public String getFilename() { return "test.png"; }
        };
        body.add("files_name", fakeImage);

        ResponseEntity<ApiResponse<List<String>>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/images",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isNotEmpty();

        testOrderRepository.findById(saved.getId()).ifPresent(testOrderRepository::delete);
    }

    @Test
    @DisplayName("GET /test-orders/{id}/images - retourne liste avec URLs → 200")
    void getImages_returns200_withUrls() {
        String token = loginAndGetToken();

        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        seed.setFilesName("[\"photo_test.png\"]");
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/images",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).get("index")).isEqualTo(0);

        testOrderRepository.findById(saved.getId()).ifPresent(testOrderRepository::delete);
    }

    @Test
    @DisplayName("DELETE /test-orders/{id}/images/0 - retire l'index 0 (suppression douce) → 200")
    void deleteImage_returns200() {
        String token = loginAndGetToken();

        TestOrder seed = new TestOrder();
        seed.setBranchId(SEED_BRANCH_ID);
        seed.setStatus(TestOrderStatus.PENDING);
        seed.setPrelevementDate(LocalDate.now());
        seed.setPatient(patientRepository.findById(seededPatientId).orElseThrow());
        seed.setFilesName("[\"fichier_inexistant.png\"]");
        TestOrder saved = testOrderRepository.save(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId() + "/images/0",
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Suppression douce : l'entrée reste à sa place, la date de retrait est posée.
        testOrderRepository.findById(saved.getId()).ifPresent(o -> {
            assertThat(o.getFilesName()).contains("fichier_inexistant.png");
            assertThat(o.getFilesDeletedAt()).isNotBlank().doesNotContain("[null]");
        });

        testOrderRepository.findById(saved.getId()).ifPresent(testOrderRepository::delete);
    }
}
