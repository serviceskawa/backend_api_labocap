package com.labo.anapath.biology.results;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une analyse d'un bon de biologie validé, et l'état de sa saisie.
 *
 * <p>Rattachée au couple (bon, analyse) — jamais à la ligne {@code detail_test_orders},
 * que la modification du bon supprime et recrée. Les deux références sont de simples
 * identifiants : l'analyse peut être supprimée (logiquement) du catalogue sans que le
 * résultat ne devienne illisible.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}) : elle n'intervient
 * que pour une analyse retirée du bon avant toute saisie.</p>
 */
@Entity
@Table(name = "biology_analysis_results")
@SQLDelete(sql = "UPDATE biology_analysis_results SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyAnalysisResult extends AuditableEntity {

    /** Bon de biologie. */
    @Column(name = "test_order_id", nullable = false, updatable = false)
    private UUID testOrderId;

    /** Analyse du catalogue (discipline BIOLOGY). */
    @Column(name = "lab_test_id", nullable = false, updatable = false)
    private UUID labTestId;

    /** État de la saisie. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BiologyAnalysisStatus status = BiologyAnalysisStatus.PENDING;

    /** Commentaire de l'analyse, imprimé sous ses résultats. */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /** Dernier utilisateur à avoir enregistré des valeurs. */
    @Column(name = "entered_by")
    private UUID enteredBy;

    /** Date du dernier enregistrement de valeurs. */
    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    /** Auteur de la validation technique en cours ({@code null} si non validée). */
    @Column(name = "tech_validated_by")
    private UUID techValidatedBy;

    /** Date de la validation technique en cours ({@code null} si non validée). */
    @Column(name = "tech_validated_at")
    private LocalDateTime techValidatedAt;
}
