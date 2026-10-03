package com.labo.anapath.common.storage;

import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.role.PermissionRepository;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un fichier n'est servi qu'à qui peut lire l'entité qui le possède : même
 * permission, et une agence à laquelle la personne a accès. Par identifiant
 * comme par l'ancienne route par chemin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AccesAuxFichiersIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo").withUsername("test").withPassword("test");

    static Path stockage;

    static {
        try {
            stockage = Files.createTempDirectory("labo-acces-fichiers-");
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
        registry.add("app.storage.path", stockage::toString);
    }

    private static final UUID AGENCE_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AGENCE_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final String ADMIN = "acces_fichiers_admin@labo.bj";
    private static final String SANS_DROIT = "acces_fichiers_patients@labo.bj";

    @Autowired private TestRestTemplate rest;
    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private Jetons jetons;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FichierStockeRepository fichiers;
    @Autowired private PatientRepository patients;
    @Autowired private TestOrderRepository demandes;

    @BeforeEach
    void jeuDeDonnees() {
        jdbc.update("""
                INSERT INTO branches (id, name, code, location, created_at, updated_at)
                SELECT ?, 'Agence B', 'B', 'Porto-Novo', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM branches WHERE id = ?)
                """, AGENCE_B, AGENCE_B);
        if (users.findByEmail(ADMIN).isEmpty()) {
            creerUtilisateur(ADMIN, roles.findBySlugAndBranchId("admin", AGENCE_A).orElseThrow());
        }
        if (users.findByEmail(SANS_DROIT).isEmpty()) {
            Role role = new Role();
            role.setBranchId(AGENCE_A);
            role.setName("Patients seulement");
            role.setSlug("patients-seulement-" + UUID.randomUUID().toString().substring(0, 8));
            role.setPermissions(List.of(permissions.findBySlug("view-patients").orElseThrow()));
            creerUtilisateur(SANS_DROIT, roles.save(role));
        }
    }

    private void creerUtilisateur(String email, Role role) {
        User u = new User();
        u.setBranchId(AGENCE_A);
        u.setFirstname("Accès");
        u.setLastname("Fichiers");
        u.setEmail(email);
        u.setPassword(encodeur.encode("Vq7-tz9Lp2-Xw4"));
        u.setActive(true);
        u.setRoles(List.of(role));
        users.save(u);
    }

    private HttpEntity<Void> connecte(String email) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jetons.pour(email));
        return new HttpEntity<>(h);
    }

    /** Écrit un fichier dans le stockage et le rattache à une demande d'examen de l'agence donnée. */
    private FichierStocke fichierDeDemande(UUID agence) throws IOException {
        Path dossier = stockage.resolve("examen_images");
        Files.createDirectories(dossier);
        String chemin = "examen_images/" + UUID.randomUUID() + ".png";
        Files.write(stockage.resolve(chemin), "PNG".getBytes());
        fichiers.rattacher(chemin, FichierStocke.TEST_ORDER, UUID.randomUUID(), agence);
        return fichiers.parChemin(chemin).orElseThrow();
    }

    private ResponseEntity<byte[]> get(String email, String suffixe) {
        return rest.exchange("http://localhost:" + port + "/api/v1/files/" + suffixe,
                HttpMethod.GET, connecte(email), byte[].class);
    }

    @Test
    @DisplayName("un fichier de l'agence B demandé par une personne de l'agence A → 403, par id comme par chemin")
    void autreAgence_403() throws IOException {
        FichierStocke f = fichierDeDemande(AGENCE_B);
        assertThat(get(ADMIN, f.getId().toString()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(ADMIN, f.getPath()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("un fichier de sa propre agence, avec la permission de l'entité → 200, par id comme par chemin")
    void memeAgenceAvecDroit_200() throws IOException {
        FichierStocke f = fichierDeDemande(AGENCE_A);
        ResponseEntity<byte[]> parId = get(ADMIN, f.getId().toString());
        assertThat(parId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parId.getBody()).isEqualTo("PNG".getBytes());
        assertThat(get(ADMIN, f.getPath()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("sans la permission de lire les demandes d'examen → 403 sur un cliché")
    void sansPermissionDeLEntite_403() throws IOException {
        FichierStocke f = fichierDeDemande(AGENCE_A);
        assertThat(get(SANS_DROIT, f.getId().toString()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("un fichier présent sur le disque mais rattaché à rien n'est plus servi → 404")
    void fichierNonRattache_404() throws IOException {
        Path dossier = stockage.resolve("documents");
        Files.createDirectories(dossier);
        String chemin = "documents/" + UUID.randomUUID() + ".pdf";
        Files.write(stockage.resolve(chemin), "PDF".getBytes());
        assertThat(get(ADMIN, chemin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("sans la permission de lire les comptes rendus, le PDF d'un compte rendu → 403")
    void pdfDeCompteRenduSansDroit_403() {
        ResponseEntity<byte[]> r = rest.exchange(
                "http://localhost:" + port + "/api/v1/reports/" + UUID.randomUUID() + "/pdf",
                HttpMethod.GET, connecte(SANS_DROIT), byte[].class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("V102 reprend les chemins existants, normalisés, avec le même identifiant que le code")
    void migrationReprendLExistant() throws IOException {
        Patient p = new Patient();
        p.setBranchId(AGENCE_A);
        p.setFirstname("Reprise");
        p.setLastname("Fichiers");
        p = patients.save(p);
        TestOrder demande = new TestOrder();
        demande.setBranchId(AGENCE_A);
        demande.setPatient(p);
        demande.setPrelevementDate(LocalDate.now());
        String cliche = "examen_images/" + UUID.randomUUID() + ".jpg";
        String archive = "examen_images/" + UUID.randomUUID() + ".pdf";
        demande.setFilesName("[\"" + cliche + "\"]");
        // Préfixe d'URL hérité : la reprise doit le retirer.
        demande.setArchive("/api/v1/files/" + archive);
        demande = demandes.save(demande);

        // Hibernate crée `permissions` depuis l'entité, qui ignore `updated_at`
        // (colonne héritée de Laravel que V98 et V102 renseignent) : on l'ajoute
        // pour jouer le script tel qu'il tournera en production.
        jdbc.execute("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP");
        String sql = new ClassPathResource("db/migration/V102__fichiers_rattaches_et_tableau_de_bord.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);

        FichierStocke f = fichiers.parChemin(cliche).orElseThrow();
        assertThat(f.getEntityType()).isEqualTo(FichierStocke.TEST_ORDER);
        assertThat(f.getEntityId()).isEqualTo(demande.getId());
        assertThat(f.getBranchId()).isEqualTo(AGENCE_A);
        assertThat(fichiers.parChemin(archive)).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM permissions WHERE slug IN ('view-dashboard', 'view-dashboard-finance')",
                Integer.class)).isEqualTo(2);
    }
}
