package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
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

/**
 * Entité représentant une catégorie d'analyses du catalogue du laboratoire.
 *
 * <p>Les catégories permettent de regrouper les analyses ({@link LabTest}) par
 * domaine médical : Hématologie, Biochimie, Cytologie, Anatomopathologie, etc.
 * Chaque catégorie est scopée par succursale via l'héritage de {@code AuditableEntity}.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).
 * Une catégorie ne peut être supprimée si des analyses y sont rattachées.</p>
 */
@Entity
@Table(name = "category_tests")
@SQLDelete(sql = "UPDATE category_tests SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class CategoryTest extends AuditableEntity {

    /** Nom de la catégorie (ex. : "Hématologie", "Biochimie"). */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Code court optionnel de la catégorie (ex. : "HEM", "BIO"). */
    @Column(name = "code", length = 50)
    private String code;

    /**
     * Discipline de la catégorie. Fixée à la création :
     * ses analyses et les remises de contrat qui la visent en dépendent.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", nullable = false, length = 20, updatable = false)
    private Discipline discipline = Discipline.PATHOLOGY;
}
