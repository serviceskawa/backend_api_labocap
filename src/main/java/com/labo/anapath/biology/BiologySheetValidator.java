package com.labo.anapath.biology;

import com.labo.anapath.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Règles de cohérence d'une fiche de paramètres, vérifiées avant toute écriture.
 *
 * <p>Les contraintes de forme (champs obligatoires, longueurs) relèvent de Bean
 * Validation sur {@link BiologySheetRequestDto} ; celles-ci portent sur le sens :
 * <ul>
 *   <li>un paramètre {@link ResultType#CHOICE} propose au moins un choix ;</li>
 *   <li>seul un paramètre {@link ResultType#NUMERIC} porte des plages chiffrées ;</li>
 *   <li>dans une plage : borne basse ≤ borne haute, seuil critique bas ≤ borne basse,
 *       borne haute ≤ seuil critique haut, seuil critique bas ≤ seuil critique haut ;
 *       au moins une borne renseignée ;</li>
 *   <li>tranche d'âge : bornes positives, âge minimal strictement inférieur à l'âge
 *       maximal (la borne haute étant exclue, une tranche {@code [n, n[} serait vide) ;</li>
 *   <li>sexe : {@code M}, {@code F} ou vide ;</li>
 *   <li>deux plages d'un même paramètre ne visent pas exactement la même population —
 *       le choix de la plage applicable deviendrait arbitraire ;</li>
 *   <li>les codes de paramètres sont uniques dans la fiche, et un même identifiant
 *       n'apparaît pas deux fois.</li>
 * </ul>
 * La méthode normalise aussi la requête en place : choix rognés et dédoublonnés,
 * choix retirés d'un paramètre qui n'est pas CHOICE, sexe en majuscule, code vide
 * ramené à {@code null}.</p>
 */
public final class BiologySheetValidator {

    /** Nombre maximal de décimales affichées. */
    static final int DECIMALES_MAX = 6;

    private BiologySheetValidator() {
    }

    /**
     * Vérifie et normalise une fiche.
     *
     * @param fiche requête à vérifier (modifiée en place)
     * @throws BusinessException à la première incohérence, avec un message qui nomme l'élément fautif
     */
    public static void validerEtNormaliser(BiologySheetRequestDto fiche) {
        Set<UUID> idsSections = new HashSet<>();
        Set<UUID> idsParametres = new HashSet<>();
        Set<UUID> idsPlages = new HashSet<>();
        Set<String> codes = new HashSet<>();

        for (BiologySheetRequestDto.SectionRequest section : nonNul(fiche.getSections())) {
            if (section == null) {
                throw new BusinessException("Une section de la fiche est vide.");
            }
            exigerUnique(idsSections, section.getId(), "section");
            for (BiologySheetRequestDto.ParameterRequest p : nonNul(section.getParameters())) {
                validerParametre(p, idsParametres, idsPlages, codes);
            }
        }
        for (BiologySheetRequestDto.ParameterRequest p : nonNul(fiche.getParameters())) {
            validerParametre(p, idsParametres, idsPlages, codes);
        }
    }

    private static void validerParametre(BiologySheetRequestDto.ParameterRequest p, Set<UUID> idsParametres,
                                         Set<UUID> idsPlages, Set<String> codes) {
        if (p == null) {
            throw new BusinessException("Un paramètre de la fiche est vide.");
        }
        String nom = p.getName() == null ? "" : p.getName().trim();
        if (nom.isEmpty()) {
            throw new BusinessException("Le nom du paramètre est obligatoire.");
        }
        if (p.getResultType() == null) {
            throw new BusinessException(prefixe(nom) + "le type de résultat est obligatoire.");
        }
        exigerUnique(idsParametres, p.getId(), "paramètre « " + nom + " »");

        if (p.getCode() != null) {
            String code = p.getCode().trim();
            p.setCode(code.isEmpty() ? null : code);
            if (p.getCode() != null && !codes.add(code.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Le code « " + code + " » est utilisé par deux paramètres de la fiche.");
            }
        }

        if (p.getDecimals() != null && (p.getDecimals() < 0 || p.getDecimals() > DECIMALES_MAX)) {
            throw new BusinessException(prefixe(nom) + "le nombre de décimales doit être compris entre 0 et "
                    + DECIMALES_MAX + ".");
        }

        if (p.getResultType() == ResultType.CHOICE) {
            List<String> choix = normaliserChoix(p.getChoices());
            if (choix.isEmpty()) {
                throw new BusinessException(prefixe(nom) + "un paramètre à choix doit proposer au moins un choix.");
            }
            p.setChoices(choix);
        } else {
            p.setChoices(null);
        }

        List<BiologySheetRequestDto.RangeRequest> plages = nonNul(p.getRanges());
        if (!plages.isEmpty() && p.getResultType() != ResultType.NUMERIC) {
            throw new BusinessException(prefixe(nom)
                    + "seul un paramètre chiffré (NUMERIC) porte des valeurs de référence chiffrées ; "
                    + "utilisez le texte de référence.");
        }
        Set<String> populations = new HashSet<>();
        for (BiologySheetRequestDto.RangeRequest r : plages) {
            validerPlage(nom, r, idsPlages);
            String population = r.getSex() + "|" + r.getAgeMinDays() + "|" + r.getAgeMaxDays();
            if (!populations.add(population)) {
                throw new BusinessException(prefixe(nom)
                        + "deux plages visent exactement la même population (sexe et tranche d'âge).");
            }
        }
    }

    private static void validerPlage(String nom, BiologySheetRequestDto.RangeRequest r, Set<UUID> idsPlages) {
        if (r == null) {
            throw new BusinessException(prefixe(nom) + "une plage de référence est vide.");
        }
        exigerUnique(idsPlages, r.getId(), "plage de référence");

        if (r.getSex() != null) {
            String sexe = r.getSex().trim().toUpperCase(Locale.ROOT);
            if (sexe.isEmpty()) {
                r.setSex(null);
            } else if (sexe.equals("M") || sexe.equals("F")) {
                r.setSex(sexe);
            } else {
                throw new BusinessException(prefixe(nom) + "le sexe d'une plage vaut M, F ou reste vide.");
            }
        }

        if ((r.getAgeMinDays() != null && r.getAgeMinDays() < 0)
                || (r.getAgeMaxDays() != null && r.getAgeMaxDays() < 0)) {
            throw new BusinessException(prefixe(nom) + "un âge ne peut pas être négatif.");
        }
        if (r.getAgeMinDays() != null && r.getAgeMaxDays() != null && r.getAgeMinDays() >= r.getAgeMaxDays()) {
            throw new BusinessException(prefixe(nom)
                    + "l'âge minimal (inclus) doit être strictement inférieur à l'âge maximal (exclu).");
        }

        if (r.getLow() == null && r.getHigh() == null && r.getCriticalLow() == null && r.getCriticalHigh() == null) {
            throw new BusinessException(prefixe(nom) + "une plage de référence doit porter au moins une borne.");
        }
        exigerOrdre(r.getLow(), r.getHigh(), nom, "la borne basse dépasse la borne haute.");
        exigerOrdre(r.getCriticalLow(), r.getLow(), nom, "le seuil critique bas dépasse la borne basse.");
        exigerOrdre(r.getHigh(), r.getCriticalHigh(), nom, "la borne haute dépasse le seuil critique haut.");
        exigerOrdre(r.getCriticalLow(), r.getCriticalHigh(), nom, "le seuil critique bas dépasse le seuil critique haut.");
    }

    /** Choix rognés, vides retirés, doublons retirés (sans tenir compte de la casse), ordre conservé. */
    static List<String> normaliserChoix(List<String> choix) {
        if (choix == null) {
            return List.of();
        }
        Set<String> vus = new HashSet<>();
        LinkedHashSet<String> resultat = new LinkedHashSet<>();
        for (String c : choix) {
            if (c == null || c.isBlank()) continue;
            String propre = c.trim();
            if (vus.add(propre.toLowerCase(Locale.ROOT))) {
                resultat.add(propre);
            }
        }
        return new ArrayList<>(resultat);
    }

    private static void exigerOrdre(BigDecimal petit, BigDecimal grand, String nom, String message) {
        if (petit != null && grand != null && petit.compareTo(grand) > 0) {
            throw new BusinessException(prefixe(nom) + message);
        }
    }

    private static void exigerUnique(Set<UUID> vus, UUID id, String quoi) {
        if (id != null && !vus.add(id)) {
            throw new BusinessException("Élément en double dans la fiche (" + quoi + ", " + id + ").");
        }
    }

    private static String prefixe(String nom) {
        return "Paramètre « " + nom + " » : ";
    }

    private static <T> List<T> nonNul(List<T> liste) {
        return Objects.requireNonNullElse(liste, List.of());
    }
}
