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
 * Section d'une fiche de paramètres de biologie (ex. : « Hémogramme »,
 * « Formule leucocytaire » dans une NFS).
 *
 * <p>Purement présentationnelle : elle regroupe des {@link BiologyParameter}
 * à l'écran de saisie et sur le compte-rendu. Un paramètre peut aussi n'appartenir
 * à aucune section.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "biology_sections")
@SQLDelete(sql = "UPDATE biology_sections SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologySection extends AuditableEntity {

    /** Analyse (discipline BIOLOGY, nature PANEL) à laquelle appartient la section. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    /** Titre affiché (ex. : « Formule leucocytaire »). */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Rang d'affichage au sein de la fiche, croissant. */
    @Column(name = "position", nullable = false)
    private int position;
}
