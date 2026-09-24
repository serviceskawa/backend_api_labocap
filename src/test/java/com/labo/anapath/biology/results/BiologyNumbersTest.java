package com.labo.anapath.biology.results;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture (virgule décimale), arrondi aux décimales du paramètre, écriture française.
 */
class BiologyNumbersTest {

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource(delimiter = '|', value = {
            "12,5      | 12.5",
            "12.5      | 12.5",
            "  7,25    | 7.25",
            "-0,3      | -0.3",
            "+4        | 4",
            ",5        | 0.5",
            "12,       | 12",
            "1 250     | 1250",
            "1 250,75  | 1250.75",
            "0         | 0",
    })
    @DisplayName("lecture : virgule ou point, espaces de milliers")
    void lecture(String brut, String attendu) {
        assertThat(BiologyNumbers.lire(brut)).isEqualByComparingTo(attendu);
    }

    @Test
    @DisplayName("lecture : espaces insécables ignorés")
    void insecables() {
        assertThat(BiologyNumbers.lire("1 250,5")).isEqualByComparingTo("1250.5");
        assertThat(BiologyNumbers.lire("1 250")).isEqualByComparingTo("1250");
    }

    @ParameterizedTest(name = "« {0} » refusé")
    @ValueSource(strings = {"", "   ", "abc", "1.234,5", "1,234.5", "1e3", "<0,5", ">1000", "12,5,3", "--1", "1-",
            "12 mg", "+", ","})
    @DisplayName("lecture : ce qui n'est pas un nombre simple est refusé")
    void refus(String brut) {
        assertThat(BiologyNumbers.lire(brut)).isNull();
    }

    @Test
    @DisplayName("lecture : null → null")
    void nul() {
        assertThat(BiologyNumbers.lire(null)).isNull();
    }

    @ParameterizedTest(name = "{0} à {1} décimale(s) → {2}")
    @CsvSource(nullValues = "-", value = {
            "12.345,  1, 12.3",
            "12.35,   1, 12.4",     // demi vers le haut
            "2.25,    1, 2.3",
            "-2.25,   1, -2.3",
            "12.5,    0, 13",
            "12,      2, 12.00",
            "12.34567, -, 12.3457", // sans décimales : échelle de la colonne
            "12.5000, -, 12.5",     // sans décimales : zéros inutiles ôtés
            "1200,    -, 1200",
            "3.14159, 6, 3.1416",   // borné à l'échelle de la colonne
    })
    @DisplayName("arrondi aux décimales du paramètre")
    void arrondi(String valeur, Short decimals, String attendu) {
        assertThat(BiologyNumbers.arrondir(new BigDecimal(valeur), decimals).toPlainString()).isEqualTo(attendu);
    }

    @Test
    @DisplayName("limite de NUMERIC(14,4)")
    void limite() {
        assertThat(BiologyNumbers.tientEnBase(new BigDecimal("9999999999.9999"))).isTrue();
        assertThat(BiologyNumbers.tientEnBase(new BigDecimal("10000000000"))).isFalse();
        assertThat(BiologyNumbers.tientEnBase(new BigDecimal("-10000000000"))).isFalse();
    }

    @Test
    @DisplayName("écriture française des valeurs de référence")
    void intervalle() {
        assertThat(BiologyNumbers.intervalle(new BigDecimal("12.0000"), new BigDecimal("16.0000"), (short) 1))
                .isEqualTo("12,0 – 16,0");
        assertThat(BiologyNumbers.intervalle(new BigDecimal("0.7000"), new BigDecimal("1.1000"), null))
                .isEqualTo("0,7 – 1,1");
        assertThat(BiologyNumbers.intervalle(new BigDecimal("3"), null, null)).isEqualTo("≥ 3");
        assertThat(BiologyNumbers.intervalle(null, new BigDecimal("5.5"), null)).isEqualTo("≤ 5,5");
        assertThat(BiologyNumbers.intervalle(null, null, null)).isNull();
        assertThat(BiologyNumbers.ecrire(new BigDecimal("150.0000"), null)).isEqualTo("150");
    }
}
