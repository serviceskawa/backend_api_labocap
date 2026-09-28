package com.labo.anapath.report;

import com.labo.anapath.role.Role;
import com.labo.anapath.test.TypeOrder;
import com.labo.anapath.test.TypeOrderRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Le périmètre borne la validation par type d'examen, et le fait de façon
 * asymétrique : borné pour qui n'est pas médecin, libre pour le médecin.
 *
 * <p>L'asymétrie est le cœur de la règle. La faute à rendre impossible est
 * d'accorder {@code validate-reports} à un secrétaire en oubliant de lui fixer
 * un périmètre : si l'absence de ligne valait « pas de restriction », cet oubli
 * lui ouvrirait le laboratoire entier.</p>
 */
@ExtendWith(MockitoExtension.class)
class PerimetreDeValidationTest {

    @Mock private PerimetreDeValidationRepository perimetres;
    @Mock private UserRepository utilisateurs;
    @Mock private TypeOrderRepository typesDExamen;
    @Mock private JournalDuPerimetreRepository journal;

    @InjectMocks private ServicePerimetreDeValidation service;

    private static final UUID QUELQU_UN = UUID.randomUUID();
    private static final UUID BRANCHE = UUID.randomUUID();

    // ---------------------------------------------------------------- outils

    private static User compte(String... slugsDeRole) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", QUELQU_UN);
        List<Role> roles = java.util.Arrays.stream(slugsDeRole).map(slug -> {
            Role r = new Role();
            ReflectionTestUtils.setField(r, "slug", slug);
            ReflectionTestUtils.setField(r, "name", slug);
            return r;
        }).toList();
        ReflectionTestUtils.setField(u, "roles", new java.util.ArrayList<>(roles));
        return u;
    }

    private static TypeOrder type(String titre) {
        TypeOrder t = new TypeOrder();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(t, "title", titre);
        return t;
    }

    private static Report compteRendu(String titreDuType) {
        Report r = new Report();
        TestOrder bon = new TestOrder();
        if (titreDuType != null) {
            bon.setTypeOrder(type(titreDuType));
        }
        r.setTestOrder(bon);
        return r;
    }

    // ------------------------------------------------------------- la règle

    @Test
    @DisplayName("Un médecin valide sans périmètre : on ne consulte même pas la table")
    void leMedecinNEstPasBorne() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("docteur")));

        assertThatCode(() -> service.exigerLePerimetre(compteRendu("Biopsie"), QUELQU_UN))
                .doesNotThrowAnyException();

        verifyNoInteractions(perimetres);
    }

    @Test
    @DisplayName("Le super-administrateur non plus")
    void leSuperAdminNonPlus() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("super-admin")));

        assertThatCode(() -> service.exigerLePerimetre(compteRendu("Histologie"), QUELQU_UN))
                .doesNotThrowAnyException();

        verifyNoInteractions(perimetres);
    }

    @Test
    @DisplayName("Un secrétaire sans périmètre ne valide rien, même avec la permission")
    void sansPerimetreLeSecretaireEstRefuse() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(perimetres.couvreLeType(QUELQU_UN, "Biopsie")).thenReturn(false);
        when(perimetres.libellesCouverts(QUELQU_UN)).thenReturn(List.of());

        assertThatThrownBy(() -> service.exigerLePerimetre(compteRendu("Biopsie"), QUELQU_UN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Aucun type d'examen ne vous est confié");
    }

    @Test
    @DisplayName("Un secrétaire valide le type qui lui est confié")
    void dansSonPerimetreLeSecretairePasse() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(perimetres.couvreLeType(QUELQU_UN, "Immuno Externe")).thenReturn(true);

        assertThatCode(() -> service.exigerLePerimetre(compteRendu("Immuno Externe"), QUELQU_UN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Hors de son périmètre, le refus nomme le type et ce qui lui est confié")
    void horsPerimetreLeRefusExplique() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(perimetres.couvreLeType(QUELQU_UN, "Biopsie")).thenReturn(false);
        when(perimetres.libellesCouverts(QUELQU_UN))
                .thenReturn(List.of("Cytologie", "Immuno Externe"));

        assertThatThrownBy(() -> service.exigerLePerimetre(compteRendu("Biopsie"), QUELQU_UN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Biopsie")
                .hasMessageContaining("Cytologie, Immuno Externe");
    }

    @Test
    @DisplayName("Un type inconnu se refuse plutôt que de passer faute de règle applicable")
    void sansTypeOnRefuse() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("laborantin")));

        assertThatThrownBy(() -> service.exigerLePerimetre(compteRendu(null), QUELQU_UN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("type d'examen de cette demande est inconnu");

        verify(perimetres, never()).couvreLeType(any(), anyString());
    }

    @Test
    @DisplayName("Un bon d'examen supprimé ne fait pas une erreur 500, mais un refus")
    void unBonIntrouvableSeRefuse() {
        // Sept comptes-rendus de la base pointent vers un bon supprimé
        // logiquement : le proxy ne se résout pas et lève EntityNotFoundException.
        // Cette garde est le premier code à déréférencer le bon pendant une
        // validation — sans filet, elle changerait une donnée ancienne en
        // erreur 500 sur un dossier qui se validait la veille.
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));

        Report r = new Report();
        ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
        TestOrder proxy = org.mockito.Mockito.mock(TestOrder.class);
        when(proxy.getTypeOrder()).thenThrow(new jakarta.persistence.EntityNotFoundException("bon absent"));
        r.setTestOrder(proxy);

        assertThatThrownBy(() -> service.exigerLePerimetre(r, QUELQU_UN))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("type d'examen de cette demande est inconnu");
    }

    // ------------------------------------------------------- l'attribution

    @Test
    @DisplayName("Accorder un libellé couvre TOUS ses doublons de type_orders")
    void unLibelleCouvreSesDoublons() {
        User beneficiaire = compte("secretariat");
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(beneficiaire));
        // La base migrée porte deux lignes par libellé. N'en enregistrer qu'une
        // ferait échouer la moitié des demandes du même type.
        when(typesDExamen.findAll()).thenReturn(List.of(
                type("Cytologie"), type("Cytologie"), type("Biopsie")));
        when(perimetres.libellesCouverts(QUELQU_UN)).thenReturn(List.of("Cytologie"));

        service.definirLePerimetre(QUELQU_UN, BRANCHE, List.of("Cytologie"), UUID.randomUUID());

        verify(perimetres).deleteByUserId(QUELQU_UN);
        // Vidé en base avant de réécrire : Hibernate ordonne les insertions
        // avant les suppressions, et sans ce flush réenregistrer un périmètre
        // qui conserve un type déjà accordé viole la contrainte d'unicité.
        verify(perimetres).flush();
        verify(perimetres, org.mockito.Mockito.times(2)).save(any(PerimetreDeValidation.class));
    }

    // ------------------------------------------------------------ la trace

    @Test
    @DisplayName("Chaque décision laisse une ligne de journal : avant → après")
    void chaqueDecisionEstJournalisee() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(typesDExamen.findAll()).thenReturn(List.of(type("Cytologie")));
        when(perimetres.libellesCouverts(QUELQU_UN))
                .thenReturn(List.of("Immuno Externe"))   // avant
                .thenReturn(List.of("Cytologie"));       // après

        service.definirLePerimetre(QUELQU_UN, BRANCHE, List.of("Cytologie"), UUID.randomUUID());

        org.mockito.ArgumentCaptor<JournalDuPerimetre> ligne =
                org.mockito.ArgumentCaptor.forClass(JournalDuPerimetre.class);
        verify(journal).save(ligne.capture());
        assertThat(ligne.getValue().getTypesAvant()).isEqualTo("Immuno Externe");
        assertThat(ligne.getValue().getTypesApres()).isEqualTo("Cytologie");
    }

    @Test
    @DisplayName("Un retrait total se lit dans le journal, pas seulement dans l'absence de lignes")
    void leRetraitEstJournalise() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(perimetres.libellesCouverts(QUELQU_UN))
                .thenReturn(List.of("Cytologie", "Immuno Externe"))
                .thenReturn(List.of());

        service.definirLePerimetre(QUELQU_UN, BRANCHE, List.of(), UUID.randomUUID());

        org.mockito.ArgumentCaptor<JournalDuPerimetre> ligne =
                org.mockito.ArgumentCaptor.forClass(JournalDuPerimetre.class);
        verify(journal).save(ligne.capture());
        assertThat(ligne.getValue().getTypesAvant()).isEqualTo("Cytologie, Immuno Externe");
        assertThat(ligne.getValue().getTypesApres()).isEmpty();
    }

    @Test
    @DisplayName("Un type inconnu n'écrit rien : ni périmètre, ni journal")
    void unTypeInconnuNEcritRien() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(perimetres.libellesCouverts(QUELQU_UN)).thenReturn(List.of());
        when(typesDExamen.findAll()).thenReturn(List.of(type("Cytologie")));

        assertThatThrownBy(() -> service.definirLePerimetre(
                QUELQU_UN, BRANCHE, List.of("Radiologie"), UUID.randomUUID()))
                .isInstanceOf(com.labo.anapath.common.exception.InvalidOperationException.class);

        // La transaction est annulée de toute façon ; journaliser une décision
        // non appliquée serait pire que ne rien journaliser.
        verify(journal, never()).save(any());
    }

    @Test
    @DisplayName("La règle rendue au journal nomme le métier et le périmètre")
    void laRegleSeLitEnClair() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(perimetres.libellesCouverts(QUELQU_UN)).thenReturn(List.of("Immuno Externe"));

        assertThat(service.sousQuelleRegle(QUELQU_UN))
                .contains("secretariat")
                .contains("Immuno Externe");
    }

    @Test
    @DisplayName("Pour un médecin, la règle dit « périmètre complet » plutôt que rien")
    void pourUnMedecinLaRegleLeDit() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("docteur")));

        assertThat(service.sousQuelleRegle(QUELQU_UN)).contains("périmètre complet");
        verifyNoInteractions(perimetres);
    }

    @Test
    @DisplayName("Enregistrer une liste vide retire tout le périmètre")
    void listeVideRetireTout() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));

        when(perimetres.libellesCouverts(QUELQU_UN)).thenReturn(List.of());

        List<String> restant = service.definirLePerimetre(
                QUELQU_UN, BRANCHE, List.of(), UUID.randomUUID());

        assertThat(restant).isEmpty();
        verify(perimetres).deleteByUserId(QUELQU_UN);
        verify(perimetres, never()).save(any());
    }

    @Test
    @DisplayName("Le libellé est comparé sans tenir compte de la casse ni des espaces")
    void leLibelleEstComparePlatement() {
        when(utilisateurs.findById(QUELQU_UN)).thenReturn(Optional.of(compte("secretariat")));
        when(typesDExamen.findAll()).thenReturn(List.of(type("Immuno Externe")));
        when(perimetres.libellesCouverts(QUELQU_UN)).thenReturn(List.of("Immuno Externe"));

        assertThatCode(() -> service.definirLePerimetre(
                QUELQU_UN, BRANCHE, List.of("  immuno externe  "), UUID.randomUUID()))
                .doesNotThrowAnyException();

        verify(perimetres).save(any(PerimetreDeValidation.class));
    }
}
