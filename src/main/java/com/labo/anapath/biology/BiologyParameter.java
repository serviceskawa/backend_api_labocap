package com.labo.anapath.biology;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.UnitMeasurement;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Paramètre d'une analyse de biologie : ce que l'on mesure ou observe
 * (ex. : « Hémoglobine », « Aspect du sérum »).
 *
 * <p>La forme du résultat est donnée par {@link #resultType} :
 * <ul>
 *   <li>{@link ResultType#NUMERIC} — valeur chiffrée, arrondie à {@link #decimals}
 *       décimales, comparée aux {@link BiologyReferenceRange valeurs de référence} ;</li>
 *   <li>{@link ResultType#TEXT} — texte libre ;</li>
 *   <li>{@link ResultType#CHOICE} — une valeur parmi {@link #choices}.</li>
 * </ul>
 * </p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}) : les résultats
 * déjà rendus gardent ainsi une référence lisible.</p>
 */
@Entity
@Table(name = "biology_parameters")
@SQLDelete(sql = "UPDATE biology_parameters SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class BiologyParameter extends AuditableEntity {

    /** Analyse (discipline BIOLOGY, nature PANEL) à laquelle appartient le paramètre. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    /** Section d'affichage, ou {@code null} pour un paramètre hors section. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private BiologySection section;

    /** Code court, unique au sein de l'analyse (ex. : « HB »). Facultatif. */
    @Column(name = "code", length = 50)
    private String code;

    /** Libellé du paramètre (ex. : « Hémoglobine »). */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Rang d'affichage au sein de sa section (ou de la fiche), croissant. */
    @Column(name = "position", nullable = false)
    private int position;

    /** Forme du résultat attendu. */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 10)
    private ResultType resultType = ResultType.NUMERIC;

    /** Choix proposés pour un paramètre {@link ResultType#CHOICE} (tableau JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", columnDefinition = "jsonb")
    private List<String> choices;

    /** Nombre de décimales affichées pour un résultat chiffré (0 à 6), ou {@code null}. */
    @Column(name = "decimals")
    private Short decimals;

    /** Unité du résultat (ex. : g/dL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_measurement_id")
    private UnitMeasurement unitMeasurement;

    /**
     * Valeurs de référence en texte libre, imprimées telles quelles quand aucune
     * plage chiffrée ne s'applique (ex. : « Négatif »).
     */
    @Column(name = "reference_text", columnDefinition = "TEXT")
    private String referenceText;

    /** {@code false} : le paramètre sert à la saisie mais n'apparaît pas sur le compte-rendu. */
    @Column(name = "printable", nullable = false)
    private boolean printable = true;

    /** {@code false} : aucun indicateur (bas, haut, critique) n'est calculé. */
    @Column(name = "flaggable", nullable = false)
    private boolean flaggable = true;
}
