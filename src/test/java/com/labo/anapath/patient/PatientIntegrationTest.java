package com.labo.anapath.patient;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.finance.Invoice;
import com.labo.anapath.finance.InvoiceRepository;
import com.labo.anapath.finance.InvoiceStatus;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.testorder.TestOrderStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
class PatientIntegrationTest {

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
    @Autowired private PatientRepository patientRepository;
    @Autowired private TestOrderRepository testOrderRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN_EMAIL = "admin_patient_test@labo.bj";
    private static final String ADMIN_PASSWORD = "adminPass123";

    @BeforeEach
    void seedAdminUser() {
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {
            Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

            User admin = new User();
            admin.setBranchId(SEED_BRANCH_ID);
            admin.setFirstname("Admin");
            admin.setLastname("Patient Test");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setActive(true);
            admin.setRoles(List.of(adminRole));
            userRepository.save(admin);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/patients";
    }

    private String loginAndGetToken() {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("POST /patients - crée un patient → 201")
    void createPatient_returns201() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        PatientRequestDto dto = new PatientRequestDto();
        dto.setCode("P-ALICE-001");
        dto.setFirstname("Alice");
        dto.setLastname("Dupont");
        dto.setTelephone1("0600000099");
        dto.setGenre("F");

        ResponseEntity<ApiResponse<PatientResponseDto>> response = restTemplate.exchange(
                baseUrl(),
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().firstname()).isEqualTo("Alice");

        patientRepository.findById(response.getBody().data().id()).ifPresent(patientRepository::delete);
    }

    @Test
    @DisplayName("POST /patients - doublon de téléphone → 409 Conflict")
    void createPatient_duplicateCode_returns409() {
        String token = loginAndGetToken();

        Patient seed = new Patient();
        seed.setFirstname("Bob");
        seed.setLastname("Martin");
        seed.setCode("P-DOUBLON-001");
        seed.setTelephone1("0600000098");
        seed.setGenre("M");
        seed.setBranchId(SEED_BRANCH_ID);
        Patient saved = patientRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            PatientRequestDto dto = new PatientRequestDto();
            dto.setCode("P-DOUBLON-001"); // même code
            dto.setFirstname("Autre");
            dto.setLastname("Patient");
            dto.setTelephone1("0600000098");
            dto.setGenre("M");

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(dto, headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            patientRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /patients/{id}/profile - retourne le profil agrégé → 200")
    void getProfile_returnsAggregatedData() {
        String token = loginAndGetToken();

        Patient patient = new Patient();
        patient.setFirstname("Claire");
        patient.setLastname("Leblanc");
        patient.setTelephone1("0600000097");
        patient.setGenre("F");
        patient.setBranchId(SEED_BRANCH_ID);
        Patient savedPatient = patientRepository.save(patient);

        TestOrder order = new TestOrder();
        order.setCode("TO-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        order.setPrelevementDate(LocalDate.now());
        order.setStatus(TestOrderStatus.PENDING);
        order.setPatient(savedPatient);
        order.setBranchId(SEED_BRANCH_ID);
        TestOrder savedOrder = testOrderRepository.save(order);

        Invoice invoice = new Invoice();
        invoice.setTestOrder(savedOrder);
        invoice.setPatient(savedPatient);
        invoice.setTotal(new BigDecimal("25000"));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setBranchId(SEED_BRANCH_ID);
        Invoice savedInvoice = invoiceRepository.save(invoice);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<PatientProfileDto>> response = restTemplate.exchange(
                    baseUrl() + "/" + savedPatient.getId() + "/profile",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PatientProfileDto profile = response.getBody().data();
            assertThat(profile.totalOrders()).isEqualTo(1);
            assertThat(profile.pendingOrders()).isEqualTo(1);
            assertThat(profile.completedOrders()).isEqualTo(0);
            assertThat(profile.totalInvoiced()).isEqualByComparingTo(new BigDecimal("25000"));
            assertThat(profile.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(profile.totalUnpaid()).isEqualByComparingTo(new BigDecimal("25000"));
            assertThat(profile.recentOrders()).hasSize(1);
            assertThat(profile.recentInvoices()).hasSize(1);
        } finally {
            invoiceRepository.delete(savedInvoice);
            testOrderRepository.delete(savedOrder);
            patientRepository.delete(savedPatient);
        }
    }

    @Test
    @DisplayName("GET /patients/{id}/profile - patient introuvable → 404")
    void getProfile_notFound_returns404() {
        String token = loginAndGetToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/" + UUID.randomUUID() + "/profile",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /patients/{id} - patient avec orders liés → 422")
    void deletePatient_withLinkedOrders_returns422() {
        String token = loginAndGetToken();

        Patient patient = new Patient();
        patient.setFirstname("Denis");
        patient.setLastname("Noir");
        patient.setTelephone1("0600000096");
        patient.setGenre("M");
        patient.setBranchId(SEED_BRANCH_ID);
        Patient savedPatient = patientRepository.save(patient);

        TestOrder order = new TestOrder();
        order.setCode("TO-DEL-" + UUID.randomUUID().toString().substring(0, 8));
        order.setPrelevementDate(LocalDate.now());
        order.setPatient(savedPatient);
        order.setBranchId(SEED_BRANCH_ID);
        TestOrder savedOrder = testOrderRepository.save(order);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl() + "/" + savedPatient.getId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            testOrderRepository.delete(savedOrder);
            patientRepository.delete(savedPatient);
        }
    }

    @Test
    @DisplayName("DELETE /patients/{id} - patient sans orders → 200 soft delete")
    void deletePatient_noLinkedOrders_returns200() {
        String token = loginAndGetToken();

        Patient patient = new Patient();
        patient.setFirstname("Eva");
        patient.setLastname("Blanc");
        patient.setTelephone1("0600000095");
        patient.setGenre("F");
        patient.setBranchId(SEED_BRANCH_ID);
        Patient saved = patientRepository.save(patient);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                baseUrl() + "/" + saved.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patientRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("GET /patients?search=Jean → 200 liste filtrée (AC-2)")
    void listPatients_withSearch_returnsFilteredResults() {
        String token = loginAndGetToken();

        Patient seed = new Patient();
        seed.setFirstname("Jean");
        seed.setLastname("SearchTest");
        seed.setTelephone1("0600000090");
        seed.setGenre("M");
        seed.setBranchId(SEED_BRANCH_ID);
        Patient saved = patientRepository.save(seed);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<ApiResponse<PageResponse<PatientResponseDto>>> response = restTemplate.exchange(
                    baseUrl() + "?search=Jean&page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).isNotEmpty();
            assertThat(response.getBody().data().content().stream()
                    .anyMatch(p -> p.firstname().equals("Jean"))).isTrue();
        } finally {
            patientRepository.delete(saved);
        }
    }

    @Test
    @DisplayName("GET /patients - sans token → 401")
    void getPatients_noToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
