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

import java.util.UUID;

/**
 * Valeur retenue pour une option de culture d'une analyse CULTURE
 * (ex. : « Aspect » → « Trouble »).
 *
 * <p>La suppression est logique : une valeur effacée à l'écran est supprimée.</p>
 */
@Entity
@Table(name = "biology_culture_results")
@SQLDelete(sql = "UPDATE biology_culture_results SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyCultureResult extends AuditableEntity {

    /** Analyse du bon à laquelle appartient la valeur. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false, updatable = false)
    private BiologyAnalysisResult analysisResult;

    /** Option de culture du référentiel. */
    @Column(name = "culture_option_id", nullable = false, updatable = false)
    private UUID cultureOptionId;

    /** Valeur retenue. */
    @Column(name = "value", columnDefinition = "TEXT")
    private String value;
}
