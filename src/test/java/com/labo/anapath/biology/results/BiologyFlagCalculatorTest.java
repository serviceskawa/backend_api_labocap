package com.labo.anapath.biology.results;

import com.labo.anapath.biology.ResultType;
import com.labo.anapath.biology.results.BiologyFlagCalculator.Bornes;
import com.labo.anapath.biology.results.BiologyFlagCalculator.Indicateur;
import com.labo.anapath.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static com.labo.anapath.biology.ResultType.CHOICE;
import static com.labo.anapath.biology.ResultType.NUMERIC;
import static com.labo.anapath.biology.ResultType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Règles de l'indicateur : bornes incluses, seuils critiques, indicateur manuel.
 */
class BiologyFlagCalculatorTest {

    private static BigDecimal d(String s) {
        return s == null || s.isEmpty() ? null : new BigDecimal(s);
    }

    /** Hémoglobine homme : normale [13 ; 17], non critique [7 ; 20]. */
    private static final Bornes HB = new Bornes(d("13"), d("17"), d("7"), d("20"));

    @Nested
    @DisplayName("calcul automatique")
    class Calcul {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "15,    N",
                "13,    N",     // borne basse incluse
                "17,    N",     // borne haute incluse
                "12.99, L",
                "17.01, H",
                "7,     L",     // seuil critique inclus dans le non-critique
                "6.99,  LL",
                "20,    H",
                "20.01, HH",
                "0,     LL",
                "-5,    LL",
                "999,   HH",
        })
        @DisplayName("bornes normales et critiques")
        void bornes(String valeur, BiologyFlag attendu) {
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, true, d(valeur), HB)).isEqualTo(attendu);
        }

        @Test
        @DisplayName("échelles différentes : 13.0000 vaut 13")
        void echelles() {
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, true, new BigDecimal("13.0000"),
                    new Bornes(new BigDecimal("13.0"), null, null, null))).isEqualTo(BiologyFlag.N);
        }

        @ParameterizedTest(name = "bornes [{0}, {1}] crit [{2}, {3}], valeur {4} → {5}")
        @CsvSource(nullValues = "-", value = {
                "5,  -,  -, -,  4,   L",
                "5,  -,  -, -,  900, N",    // pas de borne haute : jamais H
                "-,  5,  -, -,  6,   H",
                "-,  5,  -, -,  -9,  N",
                "-,  -,  2, -,  1,   LL",   // seuils critiques seuls
                "-,  -,  2, -,  3,   N",
                "-,  -,  -, 8,  9,   HH",
                "10, 20, -, -,  25,  H",    // sans seuil critique : jamais HH
                "10, 20, -, -,  1,   L",
        })
        @DisplayName("bornes partielles")
        void bornesPartielles(String low, String high, String cl, String ch, String valeur, BiologyFlag attendu) {
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, true, d(valeur), new Bornes(d(low), d(high), d(cl), d(ch))))
                    .isEqualTo(attendu);
        }

        @Test
        @DisplayName("aucune plage, ou plage sans borne : pas d'indicateur")
        void sansPlage() {
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, true, d("15"), null)).isNull();
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, true, d("15"), new Bornes(null, null, null, null)))
                    .isNull();
        }

        @Test
        @DisplayName("paramètre non signalable : jamais d'indicateur")
        void nonSignalable() {
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, false, d("999"), HB)).isNull();
        }

        @Test
        @DisplayName("TEXT et CHOICE : pas d'indicateur automatique")
        void texteEtChoix() {
            assertThat(BiologyFlagCalculator.calculer(TEXT, true, null, HB)).isNull();
            assertThat(BiologyFlagCalculator.calculer(CHOICE, true, null, HB)).isNull();
        }

        @Test
        @DisplayName("sans valeur chiffrée : pas d'indicateur")
        void sansValeur() {
            assertThat(BiologyFlagCalculator.calculer(NUMERIC, true, null, HB)).isNull();
        }
    }

    @Nested
    @DisplayName("indicateur manuel")
    class Manuel {

        @Test
        @DisplayName("absent ou blanc : le calcul est retenu, non marqué manuel")
        void absent() {
            assertThat(BiologyFlagCalculator.determiner(NUMERIC, true, d("18"), HB, null, "Hb"))
                    .isEqualTo(new Indicateur(BiologyFlag.H, false));
            assertThat(BiologyFlagCalculator.determiner(NUMERIC, true, d("18"), HB, "  ", "Hb"))
                    .isEqualTo(new Indicateur(BiologyFlag.H, false));
            assertThat(BiologyFlagCalculator.determiner(TEXT, true, null, null, null, "Aspect"))
                    .isEqualTo(new Indicateur(null, false));
        }

        @ParameterizedTest
        @CsvSource({"N, N", "l, L", "H, H", "ll, LL", " HH , HH"})
        @DisplayName("NUMERIC : N, L, H, LL, HH remplacent le calcul (casse indifférente)")
        void chiffre(String manuel, BiologyFlag attendu) {
            assertThat(BiologyFlagCalculator.determiner(NUMERIC, true, d("15"), HB, manuel, "Hb"))
                    .isEqualTo(new Indicateur(attendu, true));
        }

        @Test
        @DisplayName("NUMERIC sans plage : l'indicateur manuel est permis")
        void chiffreSansPlage() {
            assertThat(BiologyFlagCalculator.determiner(NUMERIC, true, d("15"), null, "H", "Hb"))
                    .isEqualTo(new Indicateur(BiologyFlag.H, true));
        }

        @Test
        @DisplayName("NUMERIC : « A » refusé")
        void chiffreA() {
            assertThatThrownBy(() -> BiologyFlagCalculator.determiner(NUMERIC, true, d("15"), HB, "A", "Hb"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("« Hb » est chiffré");
        }

        @Test
        @DisplayName("TEXT, CHOICE : « A » permis")
        void texteA() {
            assertThat(BiologyFlagCalculator.determiner(TEXT, true, null, null, "a", "Aspect"))
                    .isEqualTo(new Indicateur(BiologyFlag.A, true));
            assertThat(BiologyFlagCalculator.determiner(CHOICE, true, null, null, "A", "Aspect"))
                    .isEqualTo(new Indicateur(BiologyFlag.A, true));
        }

        @Test
        @DisplayName("TEXT, CHOICE : L/H/N refusés")
        void texteAutre() {
            for (String f : new String[]{"H", "L", "N", "HH"}) {
                assertThatThrownBy(() -> BiologyFlagCalculator.determiner(CHOICE, true, null, null, f, "Aspect"))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("seul l'indicateur « A »");
            }
        }

        @Test
        @DisplayName("paramètre non signalable : tout indicateur manuel est refusé")
        void nonSignalable() {
            for (ResultType t : ResultType.values()) {
                String f = t == NUMERIC ? "H" : "A";
                assertThatThrownBy(() -> BiologyFlagCalculator.determiner(t, false, d("1"), HB, f, "Remarque"))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("ne porte pas d'indicateur");
            }
        }

        @Test
        @DisplayName("indicateur inconnu : refusé")
        void inconnu() {
            assertThatThrownBy(() -> BiologyFlagCalculator.determiner(NUMERIC, true, d("15"), HB, "X", "Hb"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Indicateur « X » inconnu");
        }
    }
}
