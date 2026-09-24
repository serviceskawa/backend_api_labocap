package com.labo.anapath.biology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Choix de la valeur de référence applicable : spécificité (sexe et âge &gt; sexe &gt;
 * âge &gt; défaut), bornes d'âge [min, max[, patient au sexe ou à l'âge inconnu.
 */
class ReferenceRangeResolverTest {

    private static int compteur;

    /** Plage de référence nommée par son libellé, pour lire les assertions. */
    private static BiologyReferenceRange plage(String libelle, Character sexe, Integer ageMin, Integer ageMax) {
        BiologyReferenceRange r = new BiologyReferenceRange();
        r.setId(UUID.randomUUID());
        r.setLabel(libelle);
        r.setSex(sexe);
        r.setAgeMinDays(ageMin);
        r.setAgeMaxDays(ageMax);
        r.setLow(BigDecimal.ONE);
        r.setHigh(BigDecimal.TEN);
        r.setPosition(compteur++);
        return r;
    }

    private static String choisie(List<BiologyReferenceRange> plages, String sexe, Integer age) {
        return ReferenceRangeResolver.choose(plages, sexe, age).map(BiologyReferenceRange::getLabel).orElse(null);
    }

    // Hémoglobine : un jeu réaliste, du plus général au plus précis.
    private final BiologyReferenceRange defaut = plage("défaut", null, null, null);
    private final BiologyReferenceRange enfant = plage("enfant", null, 0, 5475);          // < 15 ans
    private final BiologyReferenceRange homme = plage("homme", 'M', null, null);
    private final BiologyReferenceRange femme = plage("femme", 'F', null, null);
    private final BiologyReferenceRange hommeAdulte = plage("homme adulte", 'M', 5475, null);
    private final BiologyReferenceRange femmeAdulte = plage("femme adulte", 'F', 5475, null);
    private final List<BiologyReferenceRange> toutes =
            List.of(defaut, enfant, homme, femme, hommeAdulte, femmeAdulte);

    @Nested
    @DisplayName("préséance")
    class Preseance {

        @Test
        @DisplayName("sexe et âge l'emportent sur sexe seul, âge seul et défaut")
        void sexeEtAge() {
            assertThat(choisie(toutes, "M", 40 * 365)).isEqualTo("homme adulte");
            assertThat(choisie(toutes, "F", 40 * 365)).isEqualTo("femme adulte");
        }

        @Test
        @DisplayName("sexe seul l'emporte sur âge seul")
        void sexeAvantAge() {
            // Un garçon de 5 ans : « enfant » (âge seul) et « homme » (sexe seul) s'appliquent.
            assertThat(choisie(toutes, "M", 5 * 365)).isEqualTo("homme");
        }

        @Test
        @DisplayName("âge seul l'emporte sur le défaut")
        void ageAvantDefaut() {
            assertThat(choisie(List.of(defaut, enfant), "M", 5 * 365)).isEqualTo("enfant");
        }

        @Test
        @DisplayName("aucun critère satisfait : la plage par défaut")
        void defaut() {
            assertThat(choisie(List.of(defaut, enfant, femme), "M", 40 * 365)).isEqualTo("défaut");
        }

        @Test
        @DisplayName("l'ordre de la liste n'influence pas la préséance")
        void ordreIndifferent() {
            List<BiologyReferenceRange> inverse = List.of(femmeAdulte, hommeAdulte, femme, homme, enfant, defaut);
            assertThat(choisie(inverse, "M", 40 * 365)).isEqualTo("homme adulte");
            assertThat(choisie(inverse, "M", 5 * 365)).isEqualTo("homme");
        }

        @Test
        @DisplayName("aucune plage applicable : résultat vide")
        void aucune() {
            assertThat(ReferenceRangeResolver.choose(List.of(femme, enfant), "M", 40 * 365)).isEmpty();
            assertThat(ReferenceRangeResolver.choose(List.of(), "M", 10)).isEmpty();
            assertThat(ReferenceRangeResolver.choose(null, "M", 10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("bornes d'âge : minimum inclus, maximum exclu")
    class Bornes {

        private final BiologyReferenceRange nouveauNe = plage("nouveau-né", null, 0, 30);
        private final BiologyReferenceRange nourrisson = plage("nourrisson", null, 30, 365);
        private final BiologyReferenceRange adulte = plage("adulte", null, 365, null);
        private final List<BiologyReferenceRange> tranches = List.of(nouveauNe, nourrisson, adulte);

        @ParameterizedTest(name = "{0} jour(s) → {1}")
        @CsvSource({
                "0,   nouveau-né",
                "29,  nouveau-né",
                "30,  nourrisson",   // borne partagée : relève de la tranche qui commence
                "364, nourrisson",
                "365, adulte",
                "36500, adulte"
        })
        void tranchesContigues(int age, String attendu) {
            assertThat(choisie(tranches, null, age)).isEqualTo(attendu);
        }

        @Test
        @DisplayName("sous la borne minimale : la tranche ne s'applique pas")
        void sousLeMinimum() {
            BiologyReferenceRange des18Ans = plage("≥ 18 ans", null, 6570, null);
            assertThat(choisie(List.of(des18Ans), null, 6569)).isNull();
            assertThat(choisie(List.of(des18Ans), null, 6570)).isEqualTo("≥ 18 ans");
        }

        @Test
        @DisplayName("borne minimale seule nulle : ouverte vers le bas")
        void minimumOuvert() {
            BiologyReferenceRange moinsDUnAn = plage("< 1 an", null, null, 365);
            assertThat(choisie(List.of(moinsDUnAn), null, 0)).isEqualTo("< 1 an");
            assertThat(choisie(List.of(moinsDUnAn), null, 364)).isEqualTo("< 1 an");
            assertThat(choisie(List.of(moinsDUnAn), null, 365)).isNull();
        }

        @Test
        @DisplayName("tranches qui se recouvrent : la plus étroite l'emporte")
        void plusEtroite() {
            BiologyReferenceRange large = plage("0-18 ans", null, 0, 6570);
            BiologyReferenceRange etroite = plage("0-1 an", null, 0, 365);
            assertThat(choisie(List.of(large, etroite), null, 100)).isEqualTo("0-1 an");
            assertThat(choisie(List.of(large, etroite), null, 400)).isEqualTo("0-18 ans");
        }

        @Test
        @DisplayName("borne haute ouverte : plus large qu'une tranche fermée")
        void ouverteEstPlusLarge() {
            BiologyReferenceRange ouverte = plage("≥ 1 an", null, 365, null);
            BiologyReferenceRange fermee = plage("1-100 ans", null, 365, 36500);
            assertThat(choisie(List.of(ouverte, fermee), null, 1000)).isEqualTo("1-100 ans");
        }

        @Test
        @DisplayName("sexe et âge : la bonne tranche du bon sexe")
        void sexeEtTranche() {
            BiologyReferenceRange garcon = plage("garçon", 'M', 0, 5475);
            BiologyReferenceRange fille = plage("fille", 'F', 0, 5475);
            BiologyReferenceRange homme = plage("homme", 'M', 5475, null);
            List<BiologyReferenceRange> plages = List.of(garcon, fille, homme);
            assertThat(choisie(plages, "M", 5474)).isEqualTo("garçon");
            assertThat(choisie(plages, "M", 5475)).isEqualTo("homme");
            assertThat(choisie(plages, "F", 5475)).isNull();
        }
    }

    @Nested
    @DisplayName("patient incomplet")
    class PatientIncomplet {

        @Test
        @DisplayName("sexe inconnu : seules les plages sans sexe s'appliquent")
        void sexeInconnu() {
            assertThat(choisie(toutes, null, 40 * 365)).isEqualTo("défaut");
            assertThat(choisie(toutes, "", 5 * 365)).isEqualTo("enfant");
            assertThat(choisie(toutes, "X", 5 * 365)).isEqualTo("enfant");
        }

        @Test
        @DisplayName("âge inconnu : seules les plages sans critère d'âge s'appliquent")
        void ageInconnu() {
            assertThat(choisie(toutes, "F", null)).isEqualTo("femme");
            assertThat(choisie(List.of(defaut, enfant), "F", null)).isEqualTo("défaut");
        }

        @Test
        @DisplayName("ni sexe ni âge : la plage par défaut, sinon rien")
        void rienDeConnu() {
            assertThat(choisie(toutes, null, null)).isEqualTo("défaut");
            assertThat(choisie(List.of(homme, enfant), null, null)).isNull();
        }
    }

    @Test
    @DisplayName("à spécificité et largeur égales, la plus petite position l'emporte")
    void departageParPosition() {
        BiologyReferenceRange b = plage("b", null, null, null);
        BiologyReferenceRange a = plage("a", null, null, null);
        a.setPosition(0);
        b.setPosition(1);
        assertThat(choisie(List.of(b, a), "M", 10)).isEqualTo("a");
    }

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource({
            "M, M", "m, M", "H, M", "Masculin, M", "homme, M", "Male, M",
            "F, F", "f, F", "Féminin, F", "FEMININ, F", "Femme, F", "female, F"
    })
    @DisplayName("le sexe de la fiche patient est normalisé")
    void normalisationDuSexe(String brut, char attendu) {
        assertThat(ReferenceRangeResolver.normaliserSexe(brut)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("sexe vide ou inconnu : null")
    void sexeNonReconnu() {
        assertThat(ReferenceRangeResolver.normaliserSexe(null)).isNull();
        assertThat(ReferenceRangeResolver.normaliserSexe("  ")).isNull();
        assertThat(ReferenceRangeResolver.normaliserSexe("Autre")).isNull();
    }

    @Test
    @DisplayName("resolve charge les plages du paramètre puis applique les règles")
    void resolveChargeLesPlages() {
        BiologyReferenceRangeRepository repository = mock(BiologyReferenceRangeRepository.class);
        BiologyParameter parametre = new BiologyParameter();
        parametre.setId(UUID.randomUUID());
        when(repository.findByParameter_IdOrderByPositionAsc(parametre.getId())).thenReturn(toutes);

        Optional<BiologyReferenceRange> r = new ReferenceRangeResolver(repository).resolve(parametre, "F", 20 * 365);

        assertThat(r).contains(femmeAdulte);
    }

    @Test
    @DisplayName("resolve sans paramètre enregistré : vide, sans requête")
    void resolveSansParametre() {
        BiologyReferenceRangeRepository repository = mock(BiologyReferenceRangeRepository.class);
        ReferenceRangeResolver resolver = new ReferenceRangeResolver(repository);

        assertThat(resolver.resolve(null, "F", 10)).isEmpty();
        assertThat(resolver.resolve(new BiologyParameter(), "F", 10)).isEmpty();
        verify(repository, never()).findByParameter_IdOrderByPositionAsc(org.mockito.ArgumentMatchers.any());
    }
}
