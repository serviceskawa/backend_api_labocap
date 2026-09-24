package com.labo.anapath.biology.results;

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
import java.util.UUID;

/**
 * Sensibilité d'un germe isolé à un antibiotique.
 *
 * <p>{@link #interpretation} vaut toujours {@code 'S'}, {@code 'I'} ou {@code 'R'} ; le
 * libellé affiché est le réglage {@code bio_antibiogram_labels} du laboratoire.</p>
 *
 * <p>La suppression est logique.</p>
 */
@Entity
@Table(name = "biology_antibiogram_results")
@SQLDelete(sql = "UPDATE biology_antibiogram_results SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyAntibiogramResult extends AuditableEntity {

    /** Germe testé. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "isolate_id", nullable = false, updatable = false)
    private BiologyIsolate isolate;

    /** Antibiotique du référentiel. */
    @Column(name = "antibiotic_id", nullable = false, updatable = false)
    private UUID antibioticId;

    /** {@code 'S'}, {@code 'I'} ou {@code 'R'}. */
    @Column(name = "interpretation", nullable = false, length = 1)
    private Character interpretation;

    /** Concentration minimale inhibitrice, en texte (ex. : « ≤0,5 »). */
    @Column(name = "mic", length = 20)
    private String mic;

    /** Diamètre d'inhibition en millimètres. */
    @Column(name = "diameter_mm", precision = 5, scale = 1)
    private BigDecimal diameterMm;
}
