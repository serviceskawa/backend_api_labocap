package com.labo.anapath.biology;

import com.labo.anapath.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cohérence d'une fiche de paramètres avant écriture.
 */
class BiologySheetValidatorTest {

    static BiologySheetRequestDto.ParameterRequest parametre(String nom, ResultType type) {
        BiologySheetRequestDto.ParameterRequest p = new BiologySheetRequestDto.ParameterRequest();
        p.setName(nom);
        p.setResultType(type);
        return p;
    }

    static BiologySheetRequestDto.RangeRequest plage(String low, String high) {
        BiologySheetRequestDto.RangeRequest r = new BiologySheetRequestDto.RangeRequest();
        r.setLow(low == null ? null : new BigDecimal(low));
        r.setHigh(high == null ? null : new BigDecimal(high));
        return r;
    }

    static BiologySheetRequestDto fiche(BiologySheetRequestDto.ParameterRequest... parametres) {
        BiologySheetRequestDto f = new BiologySheetRequestDto();
        f.setParameters(new ArrayList<>(Arrays.asList(parametres)));
        return f;
    }

    private static void refusee(BiologySheetRequestDto f, String message) {
        assertThatThrownBy(() -> BiologySheetValidator.validerEtNormaliser(f))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message);
    }

    @Test
    @DisplayName("une fiche cohérente passe")
    void ficheCoherente() {
        BiologySheetRequestDto.ParameterRequest hb = parametre("Hémoglobine", ResultType.NUMERIC);
        hb.setCode("HB");
        BiologySheetRequestDto.RangeRequest homme = plage("13", "17");
        homme.setSex("m");
        homme.setCriticalLow(new BigDecimal("7"));
        homme.setCriticalHigh(new BigDecimal("20"));
        BiologySheetRequestDto.RangeRequest femme = plage("12", "16");
        femme.setSex("F");
        BiologySheetRequestDto.RangeRequest enfant = plage("11", "14");
        enfant.setAgeMinDays(0);
        enfant.setAgeMaxDays(5475);
        hb.setRanges(List.of(homme, femme, enfant));

        assertThatCode(() -> BiologySheetValidator.validerEtNormaliser(fiche(hb))).doesNotThrowAnyException();
        assertThat(homme.getSex()).isEqualTo("M");
    }

    @Test
    @DisplayName("NUMERIC : borne basse > borne haute refusée ; égalité admise")
    void bornesInversees() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Glycémie", ResultType.NUMERIC);
        p.setRanges(List.of(plage("1.10", "0.70")));
        refusee(fiche(p), "la borne basse dépasse la borne haute");

        BiologySheetRequestDto.ParameterRequest egal = parametre("Glycémie", ResultType.NUMERIC);
        egal.setRanges(List.of(plage("1", "1.0000")));
        assertThatCode(() -> BiologySheetValidator.validerEtNormaliser(fiche(egal))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("seuils critiques hors de la normale refusés")
    void seuilsCritiques() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Potassium", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest r = plage("3.5", "5");
        r.setCriticalLow(new BigDecimal("4"));
        p.setRanges(List.of(r));
        refusee(fiche(p), "seuil critique bas dépasse la borne basse");

        BiologySheetRequestDto.ParameterRequest q = parametre("Potassium", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest s = plage("3.5", "5");
        s.setCriticalHigh(new BigDecimal("4.9"));
        q.setRanges(List.of(s));
        refusee(fiche(q), "borne haute dépasse le seuil critique haut");

        BiologySheetRequestDto.ParameterRequest c = parametre("Potassium", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest t = plage(null, null);
        t.setCriticalLow(new BigDecimal("7"));
        t.setCriticalHigh(new BigDecimal("2"));
        c.setRanges(List.of(t));
        refusee(fiche(c), "seuil critique bas dépasse le seuil critique haut");
    }

    @Test
    @DisplayName("une plage sans aucune borne est refusée")
    void plageVide() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Urée", ResultType.NUMERIC);
        p.setRanges(List.of(plage(null, null)));
        refusee(fiche(p), "au moins une borne");
    }

    @Test
    @DisplayName("âge : min < max exigé (max exclu), âges négatifs refusés")
    void ages() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Hb", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest r = plage("1", "2");
        r.setAgeMinDays(30);
        r.setAgeMaxDays(30);
        p.setRanges(List.of(r));
        refusee(fiche(p), "strictement inférieur");

        BiologySheetRequestDto.ParameterRequest q = parametre("Hb", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest s = plage("1", "2");
        s.setAgeMinDays(-1);
        q.setRanges(List.of(s));
        refusee(fiche(q), "négatif");
    }

    @Test
    @DisplayName("sexe : M, F ou vide")
    void sexe() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Hb", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest r = plage("1", "2");
        r.setSex("X");
        p.setRanges(List.of(r));
        refusee(fiche(p), "M, F");

        BiologySheetRequestDto.ParameterRequest q = parametre("Hb", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest s = plage("1", "2");
        s.setSex(" ");
        q.setRanges(List.of(s));
        BiologySheetValidator.validerEtNormaliser(fiche(q));
        assertThat(s.getSex()).isNull();
    }

    @Test
    @DisplayName("deux plages pour la même population sont refusées")
    void populationEnDouble() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Hb", ResultType.NUMERIC);
        BiologySheetRequestDto.RangeRequest a = plage("1", "2");
        a.setSex("F");
        BiologySheetRequestDto.RangeRequest b = plage("3", "4");
        b.setSex("f");
        p.setRanges(List.of(a, b));
        refusee(fiche(p), "même population");
    }

    @Test
    @DisplayName("CHOICE : au moins un choix, choix rognés et dédoublonnés")
    void choix() {
        BiologySheetRequestDto.ParameterRequest vide = parametre("Aspect", ResultType.CHOICE);
        vide.setChoices(List.of(" ", ""));
        refusee(fiche(vide), "au moins un choix");

        BiologySheetRequestDto.ParameterRequest sans = parametre("Aspect", ResultType.CHOICE);
        refusee(fiche(sans), "au moins un choix");

        BiologySheetRequestDto.ParameterRequest ok = parametre("Aspect", ResultType.CHOICE);
        ok.setChoices(Arrays.asList(" Clair ", "Trouble", "clair", null, "Hématique"));
        BiologySheetValidator.validerEtNormaliser(fiche(ok));
        assertThat(ok.getChoices()).containsExactly("Clair", "Trouble", "Hématique");
    }

    @Test
    @DisplayName("les choix d'un paramètre qui n'est pas CHOICE sont ignorés")
    void choixIgnores() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Commentaire", ResultType.TEXT);
        p.setChoices(List.of("a"));
        BiologySheetValidator.validerEtNormaliser(fiche(p));
        assertThat(p.getChoices()).isNull();
    }

    @Test
    @DisplayName("seul un paramètre NUMERIC porte des plages chiffrées")
    void plagesReserveesAuNumerique() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Aspect", ResultType.CHOICE);
        p.setChoices(List.of("Clair"));
        p.setRanges(List.of(plage("1", "2")));
        refusee(fiche(p), "seul un paramètre chiffré");
    }

    @Test
    @DisplayName("codes en double (sans tenir compte de la casse) refusés, code vide ramené à null")
    void codes() {
        BiologySheetRequestDto.ParameterRequest a = parametre("Hémoglobine", ResultType.NUMERIC);
        a.setCode("HB");
        BiologySheetRequestDto.ParameterRequest b = parametre("Hématocrite", ResultType.NUMERIC);
        b.setCode(" hb ");
        refusee(fiche(a, b), "« hb » est utilisé par deux paramètres");

        BiologySheetRequestDto.ParameterRequest c = parametre("X", ResultType.TEXT);
        c.setCode("  ");
        BiologySheetRequestDto.ParameterRequest d = parametre("Y", ResultType.TEXT);
        d.setCode("");
        BiologySheetValidator.validerEtNormaliser(fiche(c, d));
        assertThat(c.getCode()).isNull();
    }

    @Test
    @DisplayName("décimales entre 0 et 6")
    void decimales() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Hb", ResultType.NUMERIC);
        p.setDecimals((short) 7);
        refusee(fiche(p), "décimales");
    }

    @Test
    @DisplayName("un même identifiant ne figure pas deux fois")
    void identifiantEnDouble() {
        UUID id = UUID.randomUUID();
        BiologySheetRequestDto.ParameterRequest a = parametre("A", ResultType.TEXT);
        a.setId(id);
        BiologySheetRequestDto.ParameterRequest b = parametre("B", ResultType.TEXT);
        b.setId(id);
        refusee(fiche(a, b), "en double");
    }

    @Test
    @DisplayName("les paramètres des sections sont vérifiés aussi")
    void parametresDesSections() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Aspect", ResultType.CHOICE);
        BiologySheetRequestDto.SectionRequest s = new BiologySheetRequestDto.SectionRequest();
        s.setTitle("Macroscopie");
        s.setParameters(List.of(p));
        BiologySheetRequestDto f = new BiologySheetRequestDto();
        f.setSections(List.of(s));
        refusee(f, "au moins un choix");
    }
}
