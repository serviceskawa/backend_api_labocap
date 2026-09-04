package com.labo.anapath.testorder;

import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.report.TestPathologyMacroRepository;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'administration du catalogue d'étiquettes.
 *
 * <p>Trois choses s'y jouent qui échouent en silence si elles cassent : le
 * cloisonnement par branche, le fait qu'un retrait n'efface aucune trace, et
 * l'absence de doublon.</p>
 */
@ExtendWith(MockitoExtension.class)
class CatalogueEtiquettesTest {

    @Mock private TestOrderAssignmentRepository assignmentRepository;
    @Mock private TestOrderAssignmentDetailRepository detailRepository;
    @Mock private TestOrderRepository testOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private TestPathologyMacroRepository macroRepository;
    @Mock private SampleLabelRepository labelRepository;
    /**
     * Un vrai sérialiseur, non une doublure : les étiquettes passent par lui à
     * l'aller comme au retour, et une doublure ne dirait rien de ce que la
     * colonne contient réellement — qui est justement ce qu'on vérifie ici.
     */
    @org.mockito.Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks
    private TestOrderAssignmentServiceImpl service;

    private final UUID BRANCHE = UUID.randomUUID();
    private final UUID AUTRE_BRANCHE = UUID.randomUUID();

    private SampleLabel etiquette(UUID branche, String valeur) {
        SampleLabel e = new SampleLabel(branche, valeur);
        try {
            Field id = e.getClass().getSuperclass().getDeclaredField("id");
            id.setAccessible(true);
            id.set(e, UUID.randomUUID());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return e;
    }

    @Test
    @DisplayName("le catalogue d'une branche ne montre pas celui d'à côté")
    void catalogueCloisonne() {
        SampleLabel l1 = etiquette(BRANCHE, "L1");
        when(labelRepository.findByBranchIdAndDeletedAtIsNullOrderByValueAsc(BRANCHE))
                .thenReturn(List.of(l1));
        when(labelRepository.compterUsages(BRANCHE, "L1")).thenReturn(7L);

        List<EtiquetteDto> catalogue = service.catalogue(BRANCHE);

        assertThat(catalogue).singleElement()
                .satisfies(e -> {
                    assertThat(e.value()).isEqualTo("L1");
                    assertThat(e.usages()).isEqualTo(7L);
                });
    }

    @Test
    @DisplayName("on ne renomme pas l'étiquette d'une autre branche")
    void renommageCloisonne() {
        SampleLabel voisine = etiquette(AUTRE_BRANCHE, "FL1");
        when(labelRepository.findById(voisine.getId())).thenReturn(Optional.of(voisine));

        assertThatThrownBy(() -> service.renommer(BRANCHE, voisine.getId(), "FL2"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(labelRepository, never()).save(any());
        assertThat(voisine.getValue()).isEqualTo("FL1");
    }

    @Test
    @DisplayName("renommer vers un texte déjà pris est refusé")
    void pasDeDoublon() {
        SampleLabel fautive = etiquette(BRANCHE, "FLI");
        SampleLabel bonne = etiquette(BRANCHE, "FL1");
        when(labelRepository.findById(fautive.getId())).thenReturn(Optional.of(fautive));
        when(labelRepository.chercher(BRANCHE, "FL1")).thenReturn(Optional.of(bonne));

        assertThatThrownBy(() -> service.renommer(BRANCHE, fautive.getId(), "FL1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    @DisplayName("un retrait masque la proposition sans effacer la ligne")
    void retraitNEffacePas() {
        SampleLabel e = etiquette(BRANCHE, "L9");
        when(labelRepository.findById(e.getId())).thenReturn(Optional.of(e));

        service.retirer(BRANCHE, e.getId());

        // La suppression est logique : la valeur reste lisible en base, seules
        // les propositions cessent. Un `delete` dur aurait rendu illisibles les
        // affectations qui la portent.
        assertThat(e.getDeletedAt()).isNotNull();
        assertThat(e.getValue()).isEqualTo("L9");
        verify(labelRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("une étiquette vide ou trop longue est refusée")
    void bornesDuTexte() {
        // Aucun stub de lecture : le texte est jugé avant qu'on aille en base,
        // et c'est le bon ordre — un refus de forme n'a pas à toucher la base.
        SampleLabel e = etiquette(BRANCHE, "L1");

        assertThatThrownBy(() -> service.renommer(BRANCHE, e.getId(), "   "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.renommer(BRANCHE, e.getId(), "X".repeat(41)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("corriger remplace les étiquettes au lieu de les compléter")
    void laCorrectionRemplace() {
        TestOrderAssignmentDetail ligne = new TestOrderAssignmentDetail();
        ligne.setBranchId(BRANCHE);
        ligne.setLabels("[\"L1\",\"Immuno non payé\"]");
        ligne.setNote("urgent");
        when(detailRepository.findById(any())).thenReturn(java.util.Optional.of(ligne));
        when(detailRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(labelRepository.chercher(any(), any())).thenReturn(java.util.Optional.empty());

        service.modifierDetail(UUID.randomUUID(),
                new CorrectionDetailDto(List.of("L1", "Immuno payé"), null), BRANCHE);

        // « Immuno non payé » doit disparaître. Compléter au lieu de remplacer
        // laisserait les deux mentions sur le même contenant, et personne ne
        // saurait laquelle vaut.
        assertThat(ligne.getLabels()).contains("Immuno payé").doesNotContain("non payé");
    }

    @Test
    @DisplayName("une note nulle n'est pas effacée par une correction d'étiquette")
    void laNoteSurvitAUneCorrection() {
        TestOrderAssignmentDetail ligne = new TestOrderAssignmentDetail();
        ligne.setBranchId(BRANCHE);
        ligne.setNote("à traiter avant midi");
        when(detailRepository.findById(any())).thenReturn(java.util.Optional.of(ligne));
        when(detailRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(labelRepository.chercher(any(), any())).thenReturn(java.util.Optional.empty());

        service.modifierDetail(UUID.randomUUID(),
                new CorrectionDetailDto(List.of("L2"), null), BRANCHE);

        // Corriger une étiquette ne doit pas emporter une consigne qu'on
        // n'avait pas l'intention de retirer.
        assertThat(ligne.getNote()).isEqualTo("à traiter avant midi");
    }

    @Test
    @DisplayName("on ne corrige pas la ligne d'une autre branche")
    void correctionCloisonnee() {
        TestOrderAssignmentDetail ligne = new TestOrderAssignmentDetail();
        ligne.setBranchId(AUTRE_BRANCHE);
        when(detailRepository.findById(any())).thenReturn(java.util.Optional.of(ligne));

        assertThatThrownBy(() -> service.modifierDetail(UUID.randomUUID(),
                new CorrectionDetailDto(List.of("L1"), null), BRANCHE))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
