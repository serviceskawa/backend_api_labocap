package com.labo.anapath.common.audit;

import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot 6 : qui a lu quoi. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class JournalAccesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo").withUsername("test").withPassword("test");

    static Path stockage;
    static {
        try { stockage = Files.createTempDirectory("journal-acces"); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.storage.path", stockage::toString);
    }

    private static final UUID AGENCE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ADMIN = "audit_admin@labo.bj";
    private static final String SANS_DROIT = "audit_sans_droit@labo.bj";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private JournalAccesRepository journal;
    @Autowired private PurgeDesJournaux purge;
    @Autowired private com.labo.anapath.common.storage.FichierStockeRepository fichiers;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Jetons jetons;
    @LocalServerPort private int port;

    private UUID adminId;

    @BeforeEach
    void seed() {
        adminId = userRepository.findByEmail(ADMIN).orElseGet(() -> {
            Role admin = roleRepository.findBySlugAndBranchId("admin", AGENCE).orElseThrow();
            return creer(ADMIN, List.of(admin));
        }).getId();
        if (userRepository.findByEmail(SANS_DROIT).isEmpty()) creer(SANS_DROIT, List.of());
    }

    private User creer(String email, List<Role> roles) {
        User u = new User();
        u.setBranchId(AGENCE);
        u.setFirstname("Audit");
        u.setLastname("Test");
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Vq7-tz9Lp2-Xw4"));
        u.setActive(true);
        u.setRoles(roles);
        return userRepository.save(u);
    }

    private HttpEntity<Void> connecte(String email) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jetons.pour(email));
        return new HttpEntity<>(h);
    }

    private String url(String chemin) {
        return "http://localhost:" + port + "/api/v1" + chemin;
    }

    @Test
    @DisplayName("ouvrir un dossier patient → une ligne READ / PATIENT avec le bon user_id")
    void lectureDUnPatientTracee() {
        Patient p = new Patient();
        p.setFirstname("Awa"); p.setLastname("Diallo"); p.setTelephone1("0600000001"); p.setGenre("F"); p.setBranchId(AGENCE);
        UUID patientId = patientRepository.save(p).getId();

        ResponseEntity<String> r = restTemplate.exchange(url("/patients/" + patientId), HttpMethod.GET, connecte(ADMIN), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);

        // L'écriture est asynchrone.
        JournalAcces j = attendre(JournalAcces.Entite.PATIENT, patientId.toString());
        assertThat(j.getAction()).isEqualTo(JournalAcces.Action.READ);
        assertThat(j.getUserId()).isEqualTo(adminId);
        assertThat(j.getIp()).isNotBlank();
    }

    @Test
    @DisplayName("télécharger un fichier → DOWNLOAD / FILE ; le journal se lit avec view-audit, pas sans")
    void telechargementTraceEtLectureDuJournal() throws Exception {
        Path docs = stockage.resolve("documents");
        Files.createDirectories(docs);
        String nom = UUID.randomUUID() + ".pdf";
        Files.write(docs.resolve(nom), "PDF".getBytes());
        // Depuis le lot 7, un fichier ne se sert que rattaché à une entité lisible.
        fichiers.rattacher("documents/" + nom, com.labo.anapath.common.storage.FichierStocke.TEST_ORDER, UUID.randomUUID(), AGENCE);

        assertThat(restTemplate.exchange(url("/files/documents/" + nom), HttpMethod.GET, connecte(ADMIN), byte[].class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(attendre(JournalAcces.Entite.FILE, "documents/" + nom).getAction())
                .isEqualTo(JournalAcces.Action.DOWNLOAD);

        ResponseEntity<String> lecture = restTemplate.exchange(
                url("/audit/acces?entityType=FILE&entityId=documents/" + nom), HttpMethod.GET, connecte(ADMIN), String.class);
        assertThat(lecture.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lecture.getBody()).contains("\"DOWNLOAD\"").contains("documents/" + nom).contains("Test Audit");

        assertThat(restTemplate.exchange(url("/audit/acces"), HttpMethod.GET, connecte(SANS_DROIT), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("purge : une ligne de 13 mois disparaît, une de 11 mois reste")
    void purgeADouzeMois() {
        JournalAcces vieille = ligne(LocalDateTime.now().minusMonths(13));
        JournalAcces recente = ligne(LocalDateTime.now().minusMonths(11));

        purge.purger();

        assertThat(journal.findById(vieille.getId())).isEmpty();
        assertThat(journal.findById(recente.getId())).isPresent();
    }

    /** La trace part après la réponse : on lui laisse jusqu'à 5 s. */
    private JournalAcces attendre(JournalAcces.Entite entite, String entityId) {
        long limite = System.currentTimeMillis() + 5_000;
        while (true) {
            var trouvee = journal.findAll().stream()
                    .filter(j -> j.getEntityType() == entite && j.getEntityId().equals(entityId)).findFirst();
            if (trouvee.isPresent()) return trouvee.get();
            if (System.currentTimeMillis() > limite) throw new AssertionError("Aucune trace " + entite + " " + entityId);
            try { Thread.sleep(50); } catch (InterruptedException e) { throw new AssertionError(e); }
        }
    }

    private JournalAcces ligne(LocalDateTime quand) {
        JournalAcces j = new JournalAcces();
        j.setAt(quand);
        j.setUserId(adminId);
        j.setAction(JournalAcces.Action.READ);
        j.setEntityType(JournalAcces.Entite.REPORT);
        j.setEntityId(UUID.randomUUID().toString());
        return journal.save(j);
    }
}
