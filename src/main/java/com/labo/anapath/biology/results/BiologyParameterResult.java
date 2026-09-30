package com.labo.anapath.biology.results;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Valeur d'un paramètre d'une analyse PANEL, avec son indicateur et une copie de
 * l'unité et des valeurs de référence au moment de la saisie.
 *
 * <p>Les copies ({@code *Snapshot}) figent ce qui sera imprimé : modifier le
 * catalogue après coup ne réécrit pas un compte-rendu déjà rendu. Elles sont
 * reprises à chaque enregistrement de l'analyse.</p>
 *
 * <p>Le paramètre est référencé par son identifiant : supprimé (logiquement) du
 * catalogue, il ne rend pas la valeur illisible.</p>
 *
 * <p>La suppression est logique : une valeur effacée à l'écran est supprimée.</p>
 */
@Entity
@Table(name = "biology_parameter_results")
@SQLDelete(sql = "UPDATE biology_parameter_results SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyParameterResult extends AuditableEntity {

    /** Analyse du bon à laquelle appartient la valeur. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false, updatable = false)
    private BiologyAnalysisResult analysisResult;

    /** Paramètre du catalogue. */
    @Column(name = "parameter_id", nullable = false, updatable = false)
    private UUID parameterId;

    /** Valeur imprimée : nombre normalisé (NUMERIC) ou texte (TEXT, CHOICE). */
    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    /** Valeur chiffrée, pour un paramètre NUMERIC seulement. */
    @Column(name = "value_numeric", precision = 14, scale = 4)
    private BigDecimal valueNumeric;

    /** Indicateur, ou {@code null}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "flag", length = 2)
    private BiologyFlag flag;

    /** {@code true} si l'indicateur a été posé à la main plutôt que calculé. */
    @Column(name = "flag_overridden", nullable = false)
    private boolean flagOverridden;

    /** Unité au moment de la saisie. */
    @Column(name = "unit_snapshot", length = 100)
    private String unitSnapshot;

    /** Borne basse de la plage appliquée. */
    @Column(name = "low_snapshot", precision = 14, scale = 4)
    private BigDecimal lowSnapshot;

    /** Borne haute de la plage appliquée. */
    @Column(name = "high_snapshot", precision = 14, scale = 4)
    private BigDecimal highSnapshot;

    /** Seuil critique bas de la plage appliquée. */
    @Column(name = "critical_low_snapshot", precision = 14, scale = 4)
    private BigDecimal criticalLowSnapshot;

    /** Seuil critique haut de la plage appliquée. */
    @Column(name = "critical_high_snapshot", precision = 14, scale = 4)
    private BigDecimal criticalHighSnapshot;

    /** Valeurs de référence telles qu'imprimées (ex. : « 12,0 – 16,0 », « Négatif »). */
    @Column(name = "reference_snapshot", columnDefinition = "TEXT")
    private String referenceSnapshot;
}
