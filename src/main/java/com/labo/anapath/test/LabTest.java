package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
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

/**
 * Entité représentant une analyse individuelle du catalogue du laboratoire.
 *
 * <p>Une analyse (LabTest) est l'unité de base du catalogue. Elle est caractérisée par :
 * <ul>
 *   <li>Un nom et un code optionnel</li>
 *   <li>Un prix de facturation</li>
 *   <li>Des valeurs normales de référence (texte libre)</li>
 *   <li>Un statut (ACTIF / INACTIF)</li>
 *   <li>Une catégorie d'appartenance ({@link CategoryTest})</li>
 *   <li>Une unité de mesure des résultats ({@link UnitMeasurement})</li>
 * </ul>
 * </p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "lab_tests")
@SQLDelete(sql = "UPDATE lab_tests SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class LabTest extends AuditableEntity {

    /** Nom de l'analyse (ex. : "Numération Formule Sanguine"). */
    @Column(name = "name", nullable = false, length = 300)
    private String name;

    /** Code court optionnel identifiant l'analyse (ex. : "NFS"). */
    @Column(name = "code", length = 50)
    private String code;

    /** Prix de facturation de l'analyse en FCFA. Valeur minimale : 0. */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** Valeurs normales de référence sous forme de texte libre (ex. : "4,5–5,5 T/L"). */
    @Column(name = "normal_value", columnDefinition = "TEXT")
    private String normalValue;

    /**
     * Statut de l'analyse : {@code ACTIF} ou {@code INACTIF}.
     * Une analyse inactive n'apparaît pas dans les bons de demande.
     */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "ACTIF";

    /**
     * Catégorie à laquelle appartient l'analyse.
     * Chargement paresseux pour éviter les jointures systématiques.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_test_id")
    private CategoryTest categoryTest;

    /**
     * Discipline de l'analyse. Fixée à la création :
     * une analyse déjà commandée ne peut pas changer de chaîne de traitement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", nullable = false, length = 20, updatable = false)
    private Discipline discipline = Discipline.PATHOLOGY;

    /**
     * Unité de mesure des résultats de l'analyse (ex. : "mg/L", "mmol/L").
     * Chargement paresseux pour éviter les jointures systématiques.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_measurement_id")
    private UnitMeasurement unitMeasurement;
}
