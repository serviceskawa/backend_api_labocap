package com.labo.anapath.biology;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Choisit, parmi les valeurs de référence d'un paramètre, celle qui s'applique à un patient.
 *
 * <h2>Applicabilité</h2>
 * Une plage s'applique si chacun de ses critères renseignés est satisfait :
 * <ul>
 *   <li><b>Sexe</b> — égal à celui du patient. Sexe du patient inconnu : seules les
 *       plages sans sexe s'appliquent.</li>
 *   <li><b>Âge</b> — {@code ageMinDays <= âge < ageMaxDays} : borne basse <b>incluse</b>,
 *       borne haute <b>exclue</b>. Deux tranches contiguës partagent ainsi leur borne sans
 *       se chevaucher : [0, 30[ puis [30, 365[ — un nourrisson de 30 jours relève de la
 *       seconde, sans ambiguïté. Une borne nulle est ouverte. Âge inconnu : seules les
 *       plages sans critère d'âge s'appliquent.</li>
 * </ul>
 *
 * <h2>Préséance</h2>
 * La plage applicable la plus spécifique l'emporte :
 * <ol>
 *   <li>sexe <b>et</b> âge ;</li>
 *   <li>sexe seul ;</li>
 *   <li>âge seul ;</li>
 *   <li>plage par défaut (ni sexe ni âge).</li>
 * </ol>
 * À spécificité égale (deux tranches d'âge qui se recouvrent), la tranche la plus étroite
 * l'emporte — une borne ouverte comptant pour une largeur infinie — puis la plus petite
 * {@code position}, puis l'ordre de la liste.
 *
 * <p>Aucune plage applicable : résultat vide ; l'appelant se rabat sur
 * {@link BiologyParameter#getReferenceText()} et ne pose pas d'indicateur.</p>
 */
@Component
@RequiredArgsConstructor
public class ReferenceRangeResolver {

    private final BiologyReferenceRangeRepository referenceRangeRepository;

    /**
     * Plage de référence applicable à un patient pour un paramètre.
     *
     * @param parameter  paramètre concerné
     * @param patientSex sexe du patient tel que stocké ({@code "M"}, {@code "F"},
     *                   « Masculin », « Femme »…), ou {@code null} si inconnu
     * @param ageInDays  âge du patient en jours à la date du prélèvement, ou {@code null} si inconnu
     * @return la plage la plus spécifique, ou vide si aucune ne s'applique
     */
    @Transactional(readOnly = true)
    public Optional<BiologyReferenceRange> resolve(BiologyParameter parameter, String patientSex, Integer ageInDays) {
        if (parameter == null || parameter.getId() == null) {
            return Optional.empty();
        }
        return choose(referenceRangeRepository.findByParameter_IdOrderByPositionAsc(parameter.getId()),
                patientSex, ageInDays);
    }

    /**
     * Cœur du choix, sans accès à la base : applique les règles décrites sur la classe.
     *
     * @param ranges     plages candidates (celles d'un même paramètre)
     * @param patientSex sexe du patient, ou {@code null}
     * @param ageInDays  âge en jours, ou {@code null}
     * @return la plage la plus spécifique, ou vide
     */
    public static Optional<BiologyReferenceRange> choose(Collection<BiologyReferenceRange> ranges,
                                                         String patientSex, Integer ageInDays) {
        if (ranges == null || ranges.isEmpty()) {
            return Optional.empty();
        }
        Character sexe = normaliserSexe(patientSex);
        return ranges.stream()
                .filter(r -> r != null && sApplique(r, sexe, ageInDays))
                .min(Comparator.comparingInt(ReferenceRangeResolver::rangDeSpecificite)
                        .thenComparingLong(ReferenceRangeResolver::largeurDAge)
                        .thenComparingInt(BiologyReferenceRange::getPosition));
    }

    /**
     * Ramène le sexe saisi sur la fiche patient à {@code 'M'} ou {@code 'F'}.
     * Accepte les initiales et les mots usuels (« Masculin », « Homme », « Féminin »,
     * « Femme »), sans tenir compte de la casse ni des accents.
     *
     * @param sexe valeur brute
     * @return {@code 'M'}, {@code 'F'}, ou {@code null} si la valeur est vide ou inconnue
     */
    public static Character normaliserSexe(String sexe) {
        if (sexe == null || sexe.isBlank()) {
            return null;
        }
        String s = Normalizer.normalize(sexe.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return switch (s) {
            case "M", "H", "MASCULIN", "HOMME", "MALE" -> 'M';
            case "F", "FEMININ", "FEMME", "FEMALE" -> 'F';
            default -> null;
        };
    }

    private static boolean sApplique(BiologyReferenceRange r, Character sexe, Integer age) {
        if (r.hasSex() && (sexe == null || Character.toUpperCase(r.getSex()) != sexe)) {
            return false;
        }
        if (r.hasAge()) {
            if (age == null) {
                return false;
            }
            if (r.getAgeMinDays() != null && age < r.getAgeMinDays()) {
                return false;
            }
            if (r.getAgeMaxDays() != null && age >= r.getAgeMaxDays()) {
                return false;
            }
        }
        return true;
    }

    /** 0 = la plus spécifique (sexe et âge) … 3 = plage par défaut. */
    private static int rangDeSpecificite(BiologyReferenceRange r) {
        if (r.hasSex() && r.hasAge()) return 0;
        if (r.hasSex()) return 1;
        if (r.hasAge()) return 2;
        return 3;
    }

    /** Largeur de la tranche d'âge ; une borne ouverte vaut une largeur infinie. */
    private static long largeurDAge(BiologyReferenceRange r) {
        if (!r.hasAge() || r.getAgeMinDays() == null && r.getAgeMaxDays() == null) {
            return Long.MAX_VALUE;
        }
        if (r.getAgeMaxDays() == null) {
            return Long.MAX_VALUE - 1;
        }
        long min = r.getAgeMinDays() != null ? r.getAgeMinDays() : 0;
        return r.getAgeMaxDays() - min;
    }
}
