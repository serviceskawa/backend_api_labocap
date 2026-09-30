package com.labo.anapath.biology;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.test.LabTest;
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

/**
 * Option de culture retenue par une analyse CULTURE, à un rang donné.
 *
 * <p>Le couple (analyse, option) est unique parmi les lignes actives.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "lab_test_culture_options")
@SQLDelete(sql = "UPDATE lab_test_culture_options SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class LabTestCultureOption extends AuditableEntity {

    /** Analyse (discipline BIOLOGY, nature CULTURE). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    /** Option de culture retenue. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "culture_option_id", nullable = false)
    private BiologyCultureOption cultureOption;

    /** Rang d'affichage pour cette analyse, croissant. */
    @Column(name = "position", nullable = false)
    private int position;
}
