package com.labo.anapath.biology.results;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Âge d'un patient en jours, pour le choix des valeurs de référence. Pur.
 *
 * <p>Référence : la date du prélèvement (à défaut, aujourd'hui) — un résultat se
 * lit à l'âge qu'avait le patient quand on l'a prélevé.</p>
 * <ol>
 *   <li>Date de naissance connue (et pas postérieure à la référence) : jours écoulés.</li>
 *   <li>Sinon, âge déclaré : {@code yearOrMonth = false} → en mois, {@code true} ou
 *       {@code null} → en années (valeur par défaut de la fiche patient). Le patient
 *       est pris à son dernier anniversaire : « 5 ans » = exactement 5 ans avant la
 *       référence.</li>
 *   <li>Sinon, inconnu ({@code null}) : seules les plages sans critère d'âge s'appliquent.</li>
 * </ol>
 */
public final class AgeDuPatient {

    private AgeDuPatient() {
    }

    /**
     * @param naissance   date de naissance, ou {@code null}
     * @param age         âge déclaré, ou {@code null}
     * @param yearOrMonth {@code true}/{@code null} = années, {@code false} = mois
     * @param reference   date de référence (prélèvement), ou {@code null} pour aujourd'hui
     * @return l'âge en jours, ou {@code null} si inconnu
     */
    public static Integer enJours(LocalDate naissance, Integer age, Boolean yearOrMonth, LocalDate reference) {
        LocalDate ref = reference != null ? reference : LocalDate.now();
        if (naissance != null && !naissance.isAfter(ref)) {
            return Math.toIntExact(ChronoUnit.DAYS.between(naissance, ref));
        }
        if (age != null && age >= 0) {
            LocalDate depuis = Boolean.FALSE.equals(yearOrMonth) ? ref.minusMonths(age) : ref.minusYears(age);
            return Math.toIntExact(ChronoUnit.DAYS.between(depuis, ref));
        }
        return null;
    }
}
