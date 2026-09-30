package com.labo.anapath.biology;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Valeurs de référence d'un paramètre chiffré, pour une population donnée.
 *
 * <p>Les critères de population sont facultatifs :
 * <ul>
 *   <li>{@link #sex} — {@code 'M'}, {@code 'F'} ou {@code null} (les deux sexes) ;</li>
 *   <li>{@link #ageMinDays} / {@link #ageMaxDays} — tranche d'âge en jours,
 *       <b>borne basse incluse, borne haute exclue</b> : {@code [ageMin, ageMax[}.
 *       Une borne nulle est ouverte.</li>
 * </ul>
 * Le choix de la plage applicable à un patient est fait par
 * {@link ReferenceRangeResolver} : la plus spécifique l'emporte.</p>
 *
 * <p>Les bornes critiques ({@link #criticalLow}, {@link #criticalHigh}) déclenchent
 * les indicateurs LL / HH ; les bornes normales ({@link #low}, {@link #high}) les
 * indicateurs L / H.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "biology_reference_ranges")
@SQLDelete(sql = "UPDATE biology_reference_ranges SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyReferenceRange extends AuditableEntity {

    /** Paramètre concerné. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private BiologyParameter parameter;

    /** Sexe ciblé : {@code 'M'}, {@code 'F'}, ou {@code null} pour les deux. */
    @Column(name = "sex", length = 1)
    private Character sex;

    /** Âge minimal en jours, inclus ; {@code null} = pas de borne basse. */
    @Column(name = "age_min_days")
    private Integer ageMinDays;

    /** Âge maximal en jours, exclu ; {@code null} = pas de borne haute. */
    @Column(name = "age_max_days")
    private Integer ageMaxDays;

    /** Borne basse de la normale. */
    @Column(name = "low", precision = 14, scale = 4)
    private BigDecimal low;

    /** Borne haute de la normale. */
    @Column(name = "high", precision = 14, scale = 4)
    private BigDecimal high;

    /** Seuil critique bas (indicateur LL). */
    @Column(name = "critical_low", precision = 14, scale = 4)
    private BigDecimal criticalLow;

    /** Seuil critique haut (indicateur HH). */
    @Column(name = "critical_high", precision = 14, scale = 4)
    private BigDecimal criticalHigh;

    /** Libellé de la population, imprimé à côté des valeurs (ex. : « Nouveau-né »). */
    @Column(name = "label", length = 100)
    private String label;

    /** Rang d'affichage, croissant ; sert aussi à départager deux plages équivalentes. */
    @Column(name = "position", nullable = false)
    private int position;

    /** {@code true} si la plage cible un sexe. */
    public boolean hasSex() {
        return sex != null;
    }

    /** {@code true} si la plage cible une tranche d'âge (au moins une borne). */
    public boolean hasAge() {
        return ageMinDays != null || ageMaxDays != null;
    }
}
