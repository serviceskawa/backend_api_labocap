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

/**
 * Germe isolé d'une culture (ex. : « Escherichia coli », 10⁵ UFC/mL).
 *
 * <p>Porte l'antibiogramme ({@link BiologyAntibiogramResult}). La suppression est
 * logique ; supprimer un germe supprime aussi son antibiogramme.</p>
 */
@Entity
@Table(name = "biology_isolates")
@SQLDelete(sql = "UPDATE biology_isolates SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyIsolate extends AuditableEntity {

    /** Analyse CULTURE du bon. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_result_id", nullable = false, updatable = false)
    private BiologyAnalysisResult analysisResult;

    /** Germe identifié. */
    @Column(name = "organism", nullable = false, length = 200)
    private String organism;

    /** Quantité, en texte libre (ex. : « 10^5 UFC/mL », « Rares colonies »). */
    @Column(name = "quantity", length = 100)
    private String quantity;

    /** Rang d'affichage, croissant. */
    @Column(name = "position", nullable = false)
    private int position;
}
