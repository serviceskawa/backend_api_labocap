package com.labo.anapath.testorder;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportResponseDto;
import com.labo.anapath.role.PermissionRepository;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.testsupport.Jetons;
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

/**
 * Un médecin ne voit que les demandes qui lui sont confiées et les comptes
 * rendus qu'il signe ; l'administrateur voit tout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PerimetreDuMedecinIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    private static final UUID AGENCE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MEDECIN = "perimetre_medecin@labo.bj";
    private static final String ADMIN = "perimetre_admin@labo.bj";

    @Autowired private TestRestTemplate rest;
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private Jetons jetons;
    @Autowired private PatientRepository patients;
    @Autowired private TestOrderRepository demandes;
    @Autowired private ReportRepository comptesRendus;

    private User medecin;
    private TestOrder laSienne;
    private TestOrder celleDUnConfrere;

    @BeforeEach
    void jeuDeDonnees() {
        medecin = users.findByEmail(MEDECIN).orElseGet(() -> {
            Role docteur = new Role();
            docteur.setBranchId(AGENCE);
            docteur.setName("Docteur");
            // Le slug est ce qui fait le médecin — voir UserRepository.estMedecinBorne.
            docteur.setSlug("docteur");
            docteur.setPermissions(List.of(
                    permissions.findBySlug("view-test-orders").orElseThrow(),
                    permissions.findBySlug("view-reports").orElseThrow()));
            return creerUtilisateur(MEDECIN, roles.save(docteur));
        });
        if (users.findByEmail(ADMIN).isEmpty()) {
            creerUtilisateur(ADMIN, roles.findBySlugAndBranchId("admin", AGENCE).orElseThrow());
        }
        laSienne = demande("Confiée");
        laSienne.setAttribuateDoctorId(medecin.getId());
        laSienne = demandes.save(laSienne);
        celleDUnConfrere = demandes.save(demande("Confrère"));

        Report signe = compteRendu(laSienne);
        signe.setSignatory1(medecin);
        comptesRendus.save(signe);
        comptesRendus.save(compteRendu(celleDUnConfrere));
    }

    private User creerUtilisateur(String email, Role role) {
        User u = new User();
        u.setBranchId(AGENCE);
        u.setFirstname("Périmètre");
        u.setLastname("Test");
        u.setEmail(email);
        u.setPassword(encodeur.encode("Vq7-tz9Lp2-Xw4"));
        u.setActive(true);
        u.setRoles(List.of(role));
        return users.save(u);
    }

    private TestOrder demande(String nomDuPatient) {
        Patient p = new Patient();
        p.setBranchId(AGENCE);
        p.setFirstname(nomDuPatient);
        p.setLastname("Périmètre");
        p = patients.save(p);
        TestOrder t = new TestOrder();
        t.setBranchId(AGENCE);
        t.setPatient(p);
        t.setPrelevementDate(LocalDate.now());
        return t;
    }

    private Report compteRendu(TestOrder demande) {
        Report r = new Report();
        r.setBranchId(AGENCE);
        r.setTestOrder(demande);
        return r;
    }

    private HttpEntity<Void> connecte(String email) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jetons.pour(email));
        return new HttpEntity<>(h);
    }

    private <T> ResponseEntity<ApiResponse<PageResponse<T>>> page(String email, String route,
                                                                  ParameterizedTypeReference<ApiResponse<PageResponse<T>>> type) {
        return rest.exchange("http://localhost:" + port + "/api/v1" + route, HttpMethod.GET, connecte(email), type);
    }

    private static final ParameterizedTypeReference<ApiResponse<PageResponse<TestOrderResponseDto>>> DEMANDES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PageResponse<ReportResponseDto>>> COMPTES_RENDUS =
            new ParameterizedTypeReference<>() {};

    @Test
    @DisplayName("GET /test-orders : le médecin ne voit que les demandes qui lui sont confiées")
    void listeDesDemandesDuMedecin() {
        var reponse = page(MEDECIN, "/test-orders?size=100", DEMANDES);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = reponse.getBody().data().content().stream().map(TestOrderResponseDto::id).toList();
        assertThat(ids).contains(laSienne.getId()).doesNotContain(celleDUnConfrere.getId());

        // L'administrateur, lui, voit les deux.
        List<UUID> tout = page(ADMIN, "/test-orders?size=100", DEMANDES)
                .getBody().data().content().stream().map(TestOrderResponseDto::id).toList();
        assertThat(tout).contains(laSienne.getId(), celleDUnConfrere.getId());
    }

    @Test
    @DisplayName("GET /test-orders/{id} : la demande d'un confrère est introuvable pour le médecin")
    void ficheDUnConfrere_404() {
        ResponseEntity<String> ok = rest.exchange("http://localhost:" + port + "/api/v1/test-orders/" + laSienne.getId(),
                HttpMethod.GET, connecte(MEDECIN), String.class);
        ResponseEntity<String> refus = rest.exchange("http://localhost:" + port + "/api/v1/test-orders/" + celleDUnConfrere.getId(),
                HttpMethod.GET, connecte(MEDECIN), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /test-orders/index (mobile) : seules les demandes confiées")
    void indexDuMobile() {
        ResponseEntity<ApiResponse<PageDIndexDto>> reponse = rest.exchange(
                "http://localhost:" + port + "/api/v1/test-orders/index?taille=2000",
                HttpMethod.GET, connecte(MEDECIN), new ParameterizedTypeReference<>() {});
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = reponse.getBody().data().contenu().stream().map(EntreeDIndexDto::id).toList();
        assertThat(ids).contains(laSienne.getId()).doesNotContain(celleDUnConfrere.getId());
    }

    @Test
    @DisplayName("GET /reports : le médecin ne voit que les comptes rendus qu'il signe ou dont la demande lui est confiée")
    void listeDesComptesRendusDuMedecin() {
        var reponse = page(MEDECIN, "/reports?size=100", COMPTES_RENDUS);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> demandesVues = reponse.getBody().data().content().stream()
                .map(ReportResponseDto::testOrderId).toList();
        assertThat(demandesVues).contains(laSienne.getId()).doesNotContain(celleDUnConfrere.getId());

        List<UUID> tout = page(ADMIN, "/reports?size=100", COMPTES_RENDUS)
                .getBody().data().content().stream().map(ReportResponseDto::testOrderId).toList();
        assertThat(tout).contains(laSienne.getId(), celleDUnConfrere.getId());
    }
}
